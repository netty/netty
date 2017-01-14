/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.internal.hpack.Encoder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static io.netty.handler.codec.http2.Http2FrameTypes.CONTINUATION;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static io.netty.handler.codec.http2.Http2CodecUtil.writeFrameHeader;
import static io.netty.handler.codec.http2.Http2FrameTypes.HEADERS;

/**
 * Tests for {@link DefaultHttp2FrameReader}.
 */
public class DefaultHttp2FrameReaderTest {
    @Mock
    private Http2FrameListener listener;

    @Mock
    private ChannelHandlerContext ctx;

    private DefaultHttp2FrameReader frameReader;

    // Used to generate frame
    private Encoder encoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Currently, frameReader only used alloc method from ChannelHandlerContext to allocate buffer,
        // and used it as a parameter when calling listener
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

        frameReader = new DefaultHttp2FrameReader();
        encoder = new Encoder();
    }

    @After
    public void tearDown() {
        frameReader.close();
    }

    @Test
    public void readHeaderFrame() throws Http2Exception {
        final int streamId = 1;

        ByteBuf input = Unpooled.buffer();
        try {
            Http2Headers headers = new DefaultHttp2Headers()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            Http2Flags flags = new Http2Flags().endOfHeaders(true).endOfStream(true);
            writeHeaderFrame(input, streamId, headers, flags);
            frameReader.readFrame(ctx, input, listener);

            verify(listener).onHeadersRead(ctx, 1, headers, 0, true);
        } finally {
            input.release();
        }
    }

    @Test
    public void readHeaderFrameAndContinuationFrame() throws Http2Exception {
        final int streamId = 1;

        ByteBuf input = Unpooled.buffer();
        try {
            Http2Headers headers = new DefaultHttp2Headers()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            writeHeaderFrame(input, streamId, headers,
                    new Http2Flags().endOfHeaders(false).endOfStream(true));
            writeContinuationFrame(input, streamId, new DefaultHttp2Headers().add("foo", "bar"),
                    new Http2Flags().endOfHeaders(true));

            frameReader.readFrame(ctx, input, listener);

            verify(listener).onHeadersRead(ctx, 1, headers.add("foo", "bar"), 0, true);
        } finally {
            input.release();
        }
    }

    @Test(expected = Http2Exception.class)
    public void failedWhenUnknownFrameInMiddleOfHeaderBlock() throws Http2Exception {
        final int streamId = 1;

        ByteBuf input = Unpooled.buffer();
        try {
            Http2Headers headers = new DefaultHttp2Headers()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            Http2Flags flags = new Http2Flags().endOfHeaders(false).endOfStream(true);
            writeHeaderFrame(input, streamId, headers, flags);
            writeFrameHeader(input, 0, (byte) 0xff, new Http2Flags(), streamId);
            frameReader.readFrame(ctx, input, listener);
        } finally {
            input.release();
        }
    }

    private void writeHeaderFrame(
            ByteBuf output, int streamId, Http2Headers headers,
            Http2Flags flags) throws Http2Exception {
        ByteBuf headerBlock = Unpooled.buffer();
        try {
            encoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), HEADERS, flags, streamId);
            output.writeBytes(headerBlock, headerBlock.readableBytes());
        } finally {
            headerBlock.release();
        }
    }

    private void writeContinuationFrame(
            ByteBuf output, int streamId, Http2Headers headers,
            Http2Flags flags) throws Http2Exception {
        ByteBuf headerBlock = Unpooled.buffer();
        try {
            encoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), CONTINUATION, flags, streamId);
            output.writeBytes(headerBlock, headerBlock.readableBytes());
        } finally {
            headerBlock.release();
        }
    }
}
