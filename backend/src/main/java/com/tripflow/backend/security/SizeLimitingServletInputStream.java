package com.tripflow.backend.security;

import java.io.IOException;

import com.tripflow.backend.exception.PayloadTooLargeException;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

/**
 * Backstop for {@link RequestSizeLimitFilter} against a body whose actual length exceeds
 * {@code maxBytes} even though its declared {@code Content-Length} didn't (or was absent, as
 * with chunked transfer encoding) — counts bytes as they're read and throws
 * {@link PayloadTooLargeException} once the cap is crossed, rather than letting the caller
 * (Jackson, in practice) buffer an unbounded body into heap.
 */
class SizeLimitingServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maxBytes;
    private long bytesRead = 0;

    SizeLimitingServletInputStream(ServletInputStream delegate, long maxBytes) {
        this.delegate = delegate;
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = delegate.read();
        if (b != -1) {
            checkLimit(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = delegate.read(b, off, len);
        if (n > 0) {
            checkLimit(n);
        }
        return n;
    }

    private void checkLimit(int justRead) throws IOException {
        bytesRead += justRead;
        if (bytesRead > maxBytes) {
            throw new PayloadTooLargeException("Request body exceeds the maximum allowed size");
        }
    }

    @Override
    public boolean isFinished() {
        return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        delegate.setReadListener(readListener);
    }
}
