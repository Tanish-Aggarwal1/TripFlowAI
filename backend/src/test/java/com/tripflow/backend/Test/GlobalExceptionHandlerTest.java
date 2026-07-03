package com.tripflow.backend.Test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tripflow.backend.exception.ApiError;
import com.tripflow.backend.exception.GlobalExceptionHandler;
import com.tripflow.backend.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

public class GlobalExceptionHandlerTest {
	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFound_returns404WithApiError() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getRequestURI()).thenReturn("/api/trips/99");

        ResponseEntity<ApiError> response =
                handler.handleNotFound(new ResourceNotFoundException("Trip not found"), req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Trip not found");
        assertThat(response.getBody().getPath()).isEqualTo("/api/trips/99");
    }
}
