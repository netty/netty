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
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static io.netty.handler.codec.http2.Http2CodecUtil.*;
import static io.netty.handler.codec.http2.Http2FrameTypes.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


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
    private HpackEncoder hpackEncoder;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(ctx.alloc()).thenReturn(UnpooledByteBufAllocator.DEFAULT);

        frameReader = new DefaultHttp2FrameReader();
        hpackEncoder = new HpackEncoder();
    }

    @AfterEach
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

    @Test
    public void readUnknownFrame() throws Http2Exception {
        ByteBuf input = Unpooled.buffer();
        ByteBuf payload = Unpooled.buffer();
        try {
            payload.writeByte(1);

            writeFrameHeader(input, payload.readableBytes(), (byte) 0xff, new Http2Flags(), 0);
            input.writeBytes(payload);
            frameReader.readFrame(ctx, input, listener);

            verify(listener).onUnknownFrame(
                    ctx, (byte) 0xff, 0, new Http2Flags(), payload.slice(0, 1));
        } finally {
            payload.release();
            input.release();
        }
    }

    @Test
    public void failedWhenUnknownFrameInMiddleOfHeaderBlock() throws Http2Exception {
        final int streamId = 1;

        final ByteBuf input = Unpooled.buffer();
        try {
            Http2Headers headers = new DefaultHttp2Headers()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            Http2Flags flags = new Http2Flags().endOfHeaders(false).endOfStream(true);
            writeHeaderFrame(input, streamId, headers, flags);
            writeFrameHeader(input, 0, (byte) 0xff, new Http2Flags(), streamId);

            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenContinuationFrameStreamIdMismatch() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            Http2Headers headers = new DefaultHttp2Headers()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            writeHeaderFrame(input, 1, headers,
                             new Http2Flags().endOfHeaders(false).endOfStream(true));
            writeContinuationFrame(input, 3, new DefaultHttp2Headers().add("foo", "bar"),
                    new Http2Flags().endOfHeaders(true));

            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenContinuationFrameNotFollowHeaderFrame() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            writeContinuationFrame(input, 1, new DefaultHttp2Headers().add("foo", "bar"),
                                   new Http2Flags().endOfHeaders(true));
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenHeaderFrameDependsOnItself() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            Http2Headers headers = new DefaultHttp2Headers()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            writeHeaderFramePriorityPresent(
                    input, 1, headers,
                    new Http2Flags().endOfHeaders(true).endOfStream(true).priorityPresent(true),
                    1, 10);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedHeadersValidationThrowsConnectionError() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            // Because we have padding we need at least 1 byte in the payload to specify the padding length.
            writeFrameHeader(input, 0, HEADERS, new Http2Flags().paddingPresent(true), 1);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
            assertEquals(Http2Error.FRAME_SIZE_ERROR, ex.error());
        } finally {
            input.release();
        }
    }

    @Test
    public void readHeaderAndData() throws Http2Exception {
        ByteBuf input = Unpooled.buffer();
        ByteBuf dataPayload = Unpooled.buffer();
        try {
            Http2Headers headers = new DefaultHttp2Headers()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            dataPayload.writeByte(1);
            writeHeaderFrameWithData(input, 1, headers, dataPayload);

            frameReader.readFrame(ctx, input, listener);

            verify(listener).onHeadersRead(ctx, 1, headers, 0, false);
            verify(listener).onDataRead(ctx, 1, dataPayload.slice(0, 1), 0, true);
        } finally {
            input.release();
            dataPayload.release();
        }
    }

    @Test
    public void failedWhenDataFrameNotAssociateWithStream() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        ByteBuf payload = Unpooled.buffer();
        try {
            payload.writeByte(1);

            writeFrameHeader(input, payload.readableBytes(), DATA, new Http2Flags().endOfStream(true), 0);
            input.writeBytes(payload);
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
        } finally {
            payload.release();
            input.release();
        }
    }

    @Test
    public void readPriorityFrame() throws Http2Exception {
        ByteBuf input = Unpooled.buffer();
        try {
            writePriorityFrame(input, 1, 0, 10);
            frameReader.readFrame(ctx, input, listener);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenPriorityFrameDependsOnItself() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            writePriorityFrame(input, 1, 1, 10);
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenConnectionWindowUpdateFrameWithZeroDelta() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            writeFrameHeader(input, 4, WINDOW_UPDATE, new Http2Flags(), 0);
            input.writeInt(0);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenStreamWindowUpdateFrameWithZeroDelta() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            writeFrameHeader(input, 4, WINDOW_UPDATE, new Http2Flags(), 1);
            input.writeInt(0);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertInstanceOf(Http2Exception.StreamException.class, ex);
        } finally {
            input.release();
        }
    }

    @Test
    public void readSettingsFrame() throws Http2Exception {
        ByteBuf input = Unpooled.buffer();
        try {
            writeFrameHeader(input, 6, SETTINGS, new Http2Flags(), 0);
            input.writeShort(SETTINGS_MAX_HEADER_LIST_SIZE);
            input.writeInt(1024);
            frameReader.readFrame(ctx, input, listener);

            listener.onSettingsRead(ctx, new Http2Settings().maxHeaderListSize(1024));
        } finally {
            input.release();
        }
    }

    @Test
    public void readAckSettingsFrame() throws Http2Exception {
        ByteBuf input = Unpooled.buffer();
        try {
            writeFrameHeader(input, 0, SETTINGS, new Http2Flags().ack(true), 0);
            frameReader.readFrame(ctx, input, listener);

            listener.onSettingsAckRead(ctx);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenSettingsFrameOnNonZeroStream() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            writeFrameHeader(input, 6, SETTINGS, new Http2Flags(), 1);
            input.writeShort(SETTINGS_MAX_HEADER_LIST_SIZE);
            input.writeInt(1024);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenAckSettingsFrameWithPayload() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            writeFrameHeader(input, 1, SETTINGS, new Http2Flags().ack(true), 0);
            input.writeByte(1);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        } finally {
            input.release();
        }
    }

    @Test
    public void failedWhenSettingsFrameWithWrongPayloadLength() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        try {
            writeFrameHeader(input, 8, SETTINGS, new Http2Flags(), 0);
            input.writeInt(SETTINGS_MAX_HEADER_LIST_SIZE);
            input.writeInt(1024);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        } finally {
            input.release();
        }
    }

    @Test
    public void verifyValidRequestAfterMalformedPacketCausesStreamException() throws Http2Exception {
        final ByteBuf input = Unpooled.buffer();
        int priorityStreamId = 3, headerStreamId = 5;
        try {
            // Write a malformed priority header causing a stream exception in reader
            writeFrameHeader(input, 4, PRIORITY, new Http2Flags(), priorityStreamId);
            // Fill buffer with dummy payload to be properly read by reader
            input.writeByte((byte) 0x80);
            input.writeByte((byte) 0x00);
            input.writeByte((byte) 0x00);
            input.writeByte((byte) 0x7f);
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    try {
                        frameReader.readFrame(ctx, input, listener);
                    } catch (Exception e) {
                        if (e instanceof Http2Exception && Http2Exception.isStreamError((Http2Exception) e)) {
                            throw e;
                        }
                    }
                }
            });
            // Verify that after stream exception we accept new stream requests
            Http2Headers headers = new DefaultHttp2Headers()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            Http2Flags flags = new Http2Flags().endOfHeaders(true).endOfStream(true);
            writeHeaderFrame(input, headerStreamId, headers, flags);
            frameReader.readFrame(ctx, input, listener);
            verify(listener).onHeadersRead(ctx, 5, headers, 0, true);
        } finally {
            input.release();
        }
    }

    private void writeHeaderFrame(
            ByteBuf output, int streamId, Http2Headers headers,
            Http2Flags flags) throws Http2Exception {
        ByteBuf headerBlock = Unpooled.buffer();
        try {
            hpackEncoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), HEADERS, flags, streamId);
            output.writeBytes(headerBlock, headerBlock.readableBytes());
        } finally {
            headerBlock.release();
        }
    }

    private void writeHeaderFrameWithData(
            ByteBuf output, int streamId, Http2Headers headers,
            ByteBuf dataPayload) throws Http2Exception {
        ByteBuf headerBlock = Unpooled.buffer();
        try {
            hpackEncoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), HEADERS,
                    new Http2Flags().endOfHeaders(true), streamId);
            output.writeBytes(headerBlock, headerBlock.readableBytes());

            writeFrameHeader(output, dataPayload.readableBytes(), DATA, new Http2Flags().endOfStream(true), streamId);
            output.writeBytes(dataPayload);
        } finally {
            headerBlock.release();
        }
    }

    private void writeHeaderFramePriorityPresent(
            ByteBuf output, int streamId, Http2Headers headers,
            Http2Flags flags, int streamDependency, int weight) throws Http2Exception {
        ByteBuf headerBlock = Unpooled.buffer();
        try {
            headerBlock.writeInt(streamDependency);
            headerBlock.writeByte(weight - 1);
            hpackEncoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
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
            hpackEncoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), CONTINUATION, flags, streamId);
            output.writeBytes(headerBlock, headerBlock.readableBytes());
        } finally {
            headerBlock.release();
        }
    }

    private static void writePriorityFrame(
            ByteBuf output, int streamId, int streamDependency, int weight) {
        writeFrameHeader(output, 5, PRIORITY, new Http2Flags(), streamId);
        output.writeInt(streamDependency);
        output.writeByte(weight - 1);
    }
}
