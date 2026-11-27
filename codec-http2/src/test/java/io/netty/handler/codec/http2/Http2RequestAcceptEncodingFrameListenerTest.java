/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link Http2RequestAcceptEncodingFrameListener}.
 */
public class Http2RequestAcceptEncodingFrameListenerTest {

    @Mock
    private Http2FrameListener delegate;
    @Mock
    private ChannelHandlerContext ctx;

    private DefaultHttp2Connection connection;
    private Http2RequestAcceptEncodingPropertyKey key;
    private Http2RequestAcceptEncodingFrameListener listener;

    @BeforeEach
    public void setUp() {
        initMocks(this);
        connection = new DefaultHttp2Connection(true);
        key = new Http2RequestAcceptEncodingPropertyKey(connection);
        listener = new Http2RequestAcceptEncodingFrameListener(connection, delegate, key);
    }

    @Test
    public void onHeadersReadFiveArgCapturesAcceptEncodingAndDelegatesUnmodifiedHeaders() throws Http2Exception {
        Http2Stream stream = connection.remote().createStream(3, false);
        Http2Headers headers = new DefaultHttp2Headers().set("accept-encoding", "gzip");

        listener.onHeadersRead(ctx, 3, headers, 0, false);

        assertEquals("gzip", key.acceptEncoding(stream));
        assertEquals("gzip", headers.get("accept-encoding"));
        verify(delegate).onHeadersRead(eq(ctx), eq(3), eq(headers), eq(0), eq(false));
    }

    @Test
    public void onHeadersReadNineArgCapturesAcceptEncodingAndDelegatesUnmodifiedHeaders() throws Http2Exception {
        Http2Stream stream = connection.remote().createStream(3, false);
        Http2Headers headers = new DefaultHttp2Headers().set("accept-encoding", "gzip");

        listener.onHeadersRead(ctx, 3, headers, 0, (short) 16, false, 0, false);

        assertEquals("gzip", key.acceptEncoding(stream));
        assertEquals("gzip", headers.get("accept-encoding"));
        verify(delegate).onHeadersRead(eq(ctx), eq(3), eq(headers), eq(0), eq((short) 16), eq(false), eq(0),
                eq(false));
    }

    @Test
    public void onHeadersReadWithoutAcceptEncodingStoresNothing() throws Http2Exception {
        Http2Stream stream = connection.remote().createStream(3, false);
        Http2Headers headers = new DefaultHttp2Headers().set("x-foo", "bar");

        listener.onHeadersRead(ctx, 3, headers, 0, false);

        assertNull(key.acceptEncoding(stream));
        verify(delegate).onHeadersRead(eq(ctx), eq(3), eq(headers), eq(0), eq(false));
    }

    @Test
    public void onHeadersReadWithUnknownStreamDelegatesWithoutException() throws Http2Exception {
        Http2Headers headers = new DefaultHttp2Headers().set("accept-encoding", "gzip");

        listener.onHeadersRead(ctx, 99, headers, 0, false);

        verify(delegate).onHeadersRead(eq(ctx), eq(99), eq(headers), eq(0), eq(false));
    }
}
