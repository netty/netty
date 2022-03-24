package io.netty.handler.codec.http2;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.TooLongHttpLineException;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.http2.Http2Error.*;
import static org.junit.jupiter.api.Assertions.*;

class Http2ExceptionTest {

    @Test
    public void connectionErrorHandlesMessage() {
        DecoderException e = new TooLongHttpLineException("An HTTP line is larger than 1024 bytes.");
        Http2Exception http2Exception = Http2Exception.connectionError(COMPRESSION_ERROR, e, e.getMessage());
        assertEquals(COMPRESSION_ERROR, http2Exception.error());
        assertEquals("An HTTP line is larger than 1024 bytes.", http2Exception.getMessage());
    }

    @Test
    public void connectionErrorHandlesNullExceptionMessage() {
        Exception e = new RuntimeException();
        Http2Exception http2Exception = Http2Exception.connectionError(COMPRESSION_ERROR, e, e.getMessage());
        assertEquals(COMPRESSION_ERROR, http2Exception.error());
        assertEquals("Unexpected error", http2Exception.getMessage());
    }

    @Test
    public void connectionErrorHandlesMultipleMessages() {
        Exception e = new RuntimeException();
        Http2Exception http2Exception = Http2Exception.connectionError(COMPRESSION_ERROR, e, e.getMessage(), "a", "b");
        assertEquals(COMPRESSION_ERROR, http2Exception.error());
        assertEquals("Unexpected error: [a, b]", http2Exception.getMessage());
    }
}
