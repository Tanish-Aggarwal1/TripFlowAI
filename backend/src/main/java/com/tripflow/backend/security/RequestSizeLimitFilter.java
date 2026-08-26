package com.tripflow.backend.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.exception.PayloadTooLargeException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Global backstop against oversized request bodies (SCRUM-417 / security.md M-5 / backend.md
 * M6): per-field {@code @Size} annotations bound individual DTO fields, but nothing upstream
 * of Jackson deserialization stops a caller sending an arbitrarily large body in the first
 * place — Jackson buffers the whole body in heap before validation ever runs. Tomcat's own
 * size properties ({@code max-http-form-post-size}, {@code max-swallow-size}) only apply to
 * form-encoded posts, not JSON bodies, so a filter is the only place this can be enforced.
 *
 * <p>Runs ahead of Spring Security's filter chain ({@code Ordered.HIGHEST_PRECEDENCE}) so an
 * oversized body is rejected before any other filter touches it. Rejects on the declared
 * {@code Content-Length} where present, and separately wraps the input stream so a body sent
 * without a (correct) {@code Content-Length} — e.g. chunked transfer encoding — is still
 * capped while being read, not just when its length is known upfront.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(
            @Value("${app.request.max-body-size-bytes:5242880}") long maxBodyBytes,
            ObjectMapper objectMapper) {
        this.maxBodyBytes = maxBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxBodyBytes) {
            SecurityErrorWriter.write(response, objectMapper, HttpStatus.PAYLOAD_TOO_LARGE,
                    "Request body exceeds the maximum allowed size", request.getRequestURI());
            return;
        }
        chain.doFilter(new SizeLimitingRequestWrapper(request, maxBodyBytes), response);
    }

    private static final class SizeLimitingRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;

        SizeLimitingRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new SizeLimitingServletInputStream(super.getInputStream(), maxBytes);
        }
    }
}
