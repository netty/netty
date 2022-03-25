/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.TooLongHttpLineException;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
