package com.server.contestControl.authServer.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void brokenPipeIsTreatedAsClientDisconnect() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/team/stream");

        ResponseEntity<?> response = handler.handleIOException(new IOException("Broken pipe"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void sseIOExceptionIsTreatedAsClientDisconnect() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/team/stream");
        request.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);

        ResponseEntity<?> response = handler.handleIOException(new IOException("flush failed"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void wrappedBrokenPipeOnSseRequestIsTreatedAsClientDisconnect() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/team/stream");
        RuntimeException exception = new RuntimeException("stream write failed", new IOException("Broken pipe"));

        ResponseEntity<?> response = handler.handleGeneralException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void asyncRequestTimeoutReturnsNoContentForStreams() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/contest/stream");

        ResponseEntity<Void> response = handler.handleAsyncRequestTimeout(
                new AsyncRequestTimeoutException(),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void nonDisconnectIOExceptionUsesGeneralErrorResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/contests");

        ResponseEntity<?> response = handler.handleIOException(new IOException("disk unavailable"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(ErrorResponse.class);
    }
}
