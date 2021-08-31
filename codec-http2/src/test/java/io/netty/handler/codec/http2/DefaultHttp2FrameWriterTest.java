/*
 * Copyright 2017 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultHttp2FrameWriter}.
 */
public class DefaultHttp2FrameWriterTest {
    private DefaultHttp2FrameWriter frameWriter;

    private ByteBuf outbound;

    private ByteBuf expectedOutbound;

    private ChannelPromise promise;

    private Http2HeadersEncoder http2HeadersEncoder;

    @Mock
    private Channel channel;

    @Mock
    private ChannelFuture future;

    @Mock
    private ChannelHandlerContext ctx;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        http2HeadersEncoder = new DefaultHttp2HeadersEncoder(
                Http2HeadersEncoder.NEVER_SENSITIVE, new HpackEncoder(false, 16, 0));

        frameWriter = new DefaultHttp2FrameWriter(new DefaultHttp2HeadersEncoder(
                Http2HeadersEncoder.NEVER_SENSITIVE, new HpackEncoder(false, 16, 0)));

        outbound = Unpooled.buffer();

        expectedOutbound = Unpooled.EMPTY_BUFFER;

        promise = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);

        Answer<Object> answer = new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock var1) throws Throwable {
                Object msg = var1.getArgument(0);
                if (msg instanceof ByteBuf) {
                    outbound.writeBytes((ByteBuf) msg);
                }
                ReferenceCountUtil.release(msg);
                return future;
            }
        };
        when(ctx.write(any())).then(answer);
        when(ctx.write(any(), any(ChannelPromise.class))).then(answer);
        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.executor()).thenReturn(ImmediateEventExecutor.INSTANCE);
    }

    @AfterEach
    public void tearDown() throws Exception {
        outbound.release();
        expectedOutbound.release();
        frameWriter.close();
    }

    @Test
    public void writeHeaders() throws Exception {
        int streamId = 1;
        Http2Headers headers = new DefaultHttp2Headers()
                .method("GET").path("/").authority("foo.com").scheme("https");

        frameWriter.writeHeaders(ctx, streamId, headers, 0, true, promise);

        byte[] expectedPayload = headerPayload(streamId, headers);
        byte[] expectedFrameBytes = {
                (byte) 0x00, (byte) 0x00, (byte) 0x0a, // payload length = 10
                (byte) 0x01, // payload type = 1
                (byte) 0x05, // flags = (0x01 | 0x04)
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01 // stream id = 1
        };
        expectedOutbound = Unpooled.copiedBuffer(expectedFrameBytes, expectedPayload);
        assertEquals(expectedOutbound, outbound);
    }

    @Test
    public void writeHeadersWithPadding() throws Exception {
        int streamId = 1;
        Http2Headers headers = new DefaultHttp2Headers()
                .method("GET").path("/").authority("foo.com").scheme("https");

        frameWriter.writeHeaders(ctx, streamId, headers, 5, true, promise);

        byte[] expectedPayload = headerPayload(streamId, headers, (byte) 4);
        byte[] expectedFrameBytes = {
                (byte) 0x00, (byte) 0x00, (byte) 0x0f, // payload length = 16
                (byte) 0x01, // payload type = 1
                (byte) 0x0d, // flags = (0x01 | 0x04 | 0x08)
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01 // stream id = 1
        };
        expectedOutbound = Unpooled.copiedBuffer(expectedFrameBytes, expectedPayload);
        assertEquals(expectedOutbound, outbound);
    }

    @Test
    public void writeHeadersNotEndStream() throws Exception {
        int streamId = 1;
        Http2Headers headers = new DefaultHttp2Headers()
                .method("GET").path("/").authority("foo.com").scheme("https");

        frameWriter.writeHeaders(ctx, streamId, headers, 0, false, promise);

        byte[] expectedPayload = headerPayload(streamId, headers);
        byte[] expectedFrameBytes = {
                (byte) 0x00, (byte) 0x00, (byte) 0x0a, // payload length = 10
                (byte) 0x01, // payload type = 1
                (byte) 0x04, // flags = 0x04
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01 // stream id = 1
        };
        ByteBuf expectedOutbound = Unpooled.copiedBuffer(expectedFrameBytes, expectedPayload);
        assertEquals(expectedOutbound, outbound);
    }

    @Test
    public void writeEmptyDataWithPadding() {
        int streamId = 1;

        ByteBuf payloadByteBuf = Unpooled.buffer();
        frameWriter.writeData(ctx, streamId, payloadByteBuf, 2, true, promise);

        assertEquals(0, payloadByteBuf.refCnt());

        byte[] expectedFrameBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x02, // payload length
            (byte) 0x00, // payload type
            (byte) 0x09, // flags
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, // stream id
            (byte) 0x01, (byte) 0x00, // padding
        };
        expectedOutbound = Unpooled.copiedBuffer(expectedFrameBytes);
        assertEquals(expectedOutbound, outbound);
    }

    /**
     * Test large headers that exceed {@link DefaultHttp2FrameWriter#maxFrameSize()}
     * the remaining headers will be sent in a CONTINUATION frame
     */
    @Test
    public void writeLargeHeaders() throws Exception {
        int streamId = 1;
        Http2Headers headers = new DefaultHttp2Headers()
                .method("GET").path("/").authority("foo.com").scheme("https");
        headers = dummyHeaders(headers, 20);

        http2HeadersEncoder.configuration().maxHeaderListSize(Integer.MAX_VALUE);
        frameWriter.headersConfiguration().maxHeaderListSize(Integer.MAX_VALUE);
        frameWriter.maxFrameSize(Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
        frameWriter.writeHeaders(ctx, streamId, headers, 0, true, promise);

        byte[] expectedPayload = headerPayload(streamId, headers);

        // First frame: HEADER(length=0x4000, flags=0x01)
        assertEquals(Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND,
                     outbound.readUnsignedMedium());
        assertEquals(0x01, outbound.readByte());
        assertEquals(0x01, outbound.readByte());
        assertEquals(streamId, outbound.readInt());

        byte[] firstPayload = new byte[Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND];
        outbound.readBytes(firstPayload);

        int remainPayloadLength = expectedPayload.length - Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND;
        // Second frame: CONTINUATION(length=remainPayloadLength, flags=0x04)
        assertEquals(remainPayloadLength, outbound.readUnsignedMedium());
        assertEquals(0x09, outbound.readByte());
        assertEquals(0x04, outbound.readByte());
        assertEquals(streamId, outbound.readInt());

        byte[] secondPayload = new byte[remainPayloadLength];
        outbound.readBytes(secondPayload);

        assertArrayEquals(Arrays.copyOfRange(expectedPayload, 0, firstPayload.length),
                          firstPayload);
        assertArrayEquals(Arrays.copyOfRange(expectedPayload, firstPayload.length,
                                             expectedPayload.length),
                          secondPayload);
    }

    @Test
    public void writeLargeHeaderWithPadding() throws Exception {
        int streamId = 1;
        Http2Headers headers = new DefaultHttp2Headers()
                .method("GET").path("/").authority("foo.com").scheme("https");
        headers = dummyHeaders(headers, 20);

        http2HeadersEncoder.configuration().maxHeaderListSize(Integer.MAX_VALUE);
        frameWriter.headersConfiguration().maxHeaderListSize(Integer.MAX_VALUE);
        frameWriter.maxFrameSize(Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);
        frameWriter.writeHeaders(ctx, streamId, headers, 5, true, promise);

        byte[] expectedPayload = buildLargeHeaderPayload(streamId, headers, (byte) 4,
                Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND);

        // First frame: HEADER(length=0x4000, flags=0x09)
        assertEquals(Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND,
                outbound.readUnsignedMedium());
        assertEquals(0x01, outbound.readByte());
        assertEquals(0x09, outbound.readByte()); // 0x01 + 0x08
        assertEquals(streamId, outbound.readInt());

        byte[] firstPayload = new byte[Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND];
        outbound.readBytes(firstPayload);

        int remainPayloadLength = expectedPayload.length - Http2CodecUtil.MAX_FRAME_SIZE_LOWER_BOUND;
        // Second frame: CONTINUATION(length=remainPayloadLength, flags=0x04)
        assertEquals(remainPayloadLength, outbound.readUnsignedMedium());
        assertEquals(0x09, outbound.readByte());
        assertEquals(0x04, outbound.readByte());
        assertEquals(streamId, outbound.readInt());

        byte[] secondPayload = new byte[remainPayloadLength];
        outbound.readBytes(secondPayload);

        assertArrayEquals(Arrays.copyOfRange(expectedPayload, 0, firstPayload.length),
                firstPayload);
        assertArrayEquals(Arrays.copyOfRange(expectedPayload, firstPayload.length,
                expectedPayload.length),
                secondPayload);
    }

    @Test
    public void writeFrameZeroPayload() throws Exception {
        frameWriter.writeFrame(ctx, (byte) 0xf, 0, new Http2Flags(), Unpooled.EMPTY_BUFFER, promise);

        byte[] expectedFrameBytes = {
                (byte) 0x00, (byte) 0x00, (byte) 0x00, // payload length
                (byte) 0x0f, // payload type
                (byte) 0x00, // flags
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 // stream id
        };

        expectedOutbound = Unpooled.wrappedBuffer(expectedFrameBytes);
        assertEquals(expectedOutbound, outbound);
    }

    @Test
    public void writeFrameHasPayload() throws Exception {
        byte[] payload = {(byte) 0x01, (byte) 0x03, (byte) 0x05, (byte) 0x07, (byte) 0x09};

        // will auto release after frameWriter.writeFrame succeed
        ByteBuf payloadByteBuf = Unpooled.wrappedBuffer(payload);
        frameWriter.writeFrame(ctx, (byte) 0xf, 0, new Http2Flags(), payloadByteBuf, promise);

        byte[] expectedFrameHeaderBytes = {
                (byte) 0x00, (byte) 0x00, (byte) 0x05, // payload length
                (byte) 0x0f, // payload type
                (byte) 0x00, // flags
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00 // stream id
        };
        expectedOutbound = Unpooled.copiedBuffer(expectedFrameHeaderBytes, payload);
        assertEquals(expectedOutbound, outbound);
    }

    @Test
    public void writePriority() {
        frameWriter.writePriority(
            ctx, /* streamId= */ 1, /* dependencyId= */ 2, /* weight= */ (short) 256, /* exclusive= */ true, promise);

        expectedOutbound = Unpooled.copiedBuffer(new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0x05, // payload length = 5
                (byte) 0x02, // payload type = 2
                (byte) 0x00, // flags = 0x00
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, // stream id = 1
                (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x02, // dependency id = 2 | exclusive = 1 << 63
                (byte) 0xFF, // weight = 255 (implicit +1)
        });
        assertEquals(expectedOutbound, outbound);
    }

    @Test
    public void writePriorityDefaults() {
        frameWriter.writePriority(
            ctx, /* streamId= */ 1, /* dependencyId= */ 0, /* weight= */ (short) 16, /* exclusive= */ false, promise);

        expectedOutbound = Unpooled.copiedBuffer(new byte[] {
                (byte) 0x00, (byte) 0x00, (byte) 0x05, // payload length = 5
                (byte) 0x02, // payload type = 2
                (byte) 0x00, // flags = 0x00
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, // stream id = 1
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, // dependency id = 0 | exclusive = 0 << 63
                (byte) 0x0F, // weight = 15 (implicit +1)
        });
        assertEquals(expectedOutbound, outbound);
    }

    private byte[] headerPayload(int streamId, Http2Headers headers, byte padding) throws Http2Exception, IOException {
        if (padding == 0) {
            return headerPayload(streamId, headers);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(padding);
            outputStream.write(headerPayload(streamId, headers));
            outputStream.write(new byte[padding]);
            return outputStream.toByteArray();
        } finally {
            outputStream.close();
        }
    }

    private byte[] headerPayload(int streamId, Http2Headers headers) throws Http2Exception {
        ByteBuf byteBuf = Unpooled.buffer();
        try {
            http2HeadersEncoder.encodeHeaders(streamId, headers, byteBuf);
            byte[] bytes = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(bytes);
            return bytes;
        } finally {
            byteBuf.release();
        }
    }

    private byte[] buildLargeHeaderPayload(int streamId, Http2Headers headers, byte padding, int maxFrameSize)
            throws Http2Exception, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            outputStream.write(padding);
            byte[] payload = headerPayload(streamId, headers);
            int firstPayloadSize = maxFrameSize - (padding + 1); //1 for padding length
            outputStream.write(payload, 0, firstPayloadSize);
            outputStream.write(new byte[padding]);
            outputStream.write(payload, firstPayloadSize, payload.length - firstPayloadSize);
            return outputStream.toByteArray();
        } finally {
            outputStream.close();
        }
    }

    private static Http2Headers dummyHeaders(Http2Headers headers, int times) {
        final String largeValue = repeat("dummy-value", 100);
        for (int i = 0; i < times; i++) {
            headers.add(String.format("dummy-%d", i), largeValue);
        }
        return headers;
    }

    private static String repeat(String str, int count) {
        return String.format(String.format("%%%ds", count), " ").replace(" ", str);
    }
}
