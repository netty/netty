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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_HEADER_LIST_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.writeFrameHeader;
import static io.netty5.handler.codec.http2.Http2FrameTypes.CONTINUATION;
import static io.netty5.handler.codec.http2.Http2FrameTypes.DATA;
import static io.netty5.handler.codec.http2.Http2FrameTypes.HEADERS;
import static io.netty5.handler.codec.http2.Http2FrameTypes.PRIORITY;
import static io.netty5.handler.codec.http2.Http2FrameTypes.SETTINGS;
import static io.netty5.handler.codec.http2.Http2FrameTypes.WINDOW_UPDATE;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * Tests for {@link DefaultHttp2FrameReader}.
 */
public class DefaultHttp2FrameReaderTest {
    @Mock
    private Http2FrameListener listener;

    @Mock
    private ChannelHandlerContext ctx;

    @AutoClose
    private DefaultHttp2FrameReader frameReader;

    // Used to generate frame
    private HpackEncoder hpackEncoder;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(ctx.bufferAllocator()).thenReturn(onHeapAllocator());

        frameReader = new DefaultHttp2FrameReader();
        hpackEncoder = new HpackEncoder();
    }

    @Test
    public void readHeaderFrame() throws Http2Exception {
        final int streamId = 1;

        try (Buffer input = onHeapAllocator().allocate(256)) {
            Http2Headers headers = Http2Headers.newHeaders()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            Http2Flags flags = new Http2Flags().endOfHeaders(true).endOfStream(true);
            writeHeaderFrame(input, streamId, headers, flags);
            frameReader.readFrame(ctx, input, listener);

            verify(listener).onHeadersRead(ctx, 1, headers, 0, true);
        }
    }

    @Test
    public void readHeaderFrameAndContinuationFrame() throws Http2Exception {
        final int streamId = 1;

        try (Buffer input = onHeapAllocator().allocate(256)) {
            Http2Headers headers = Http2Headers.newHeaders()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            writeHeaderFrame(input, streamId, headers,
                    new Http2Flags().endOfHeaders(false).endOfStream(true));
            writeContinuationFrame(input, streamId, Http2Headers.newHeaders().add("foo", "bar"),
                    new Http2Flags().endOfHeaders(true));

            frameReader.readFrame(ctx, input, listener);

            verify(listener).onHeadersRead(ctx, 1, headers.add("foo", "bar"), 0, true);
        }
    }

    @Test
    public void readUnknownFrame() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256);
             Buffer payload = onHeapAllocator().allocate(256)) {
            payload.writeByte((byte) 1);

            writeFrameHeader(input, payload.readableBytes(), (byte) 0xff, new Http2Flags(), 0);
            input.writeBytes(payload);

            doAnswer(invocation -> {
                assertEquals(ctx, invocation.getArgument(0));
                assertEquals((byte) 0xff, (byte) invocation.getArgument(1));
                assertEquals(0, (int) invocation.getArgument(2));
                assertEquals(new Http2Flags(), (Http2Flags) invocation.getArgument(3));
                assertEquals(payload.readerOffset(0), (Buffer) invocation.getArgument(4));
                return 0;
            }).when(listener)
                    .onUnknownFrame(any(ChannelHandlerContext.class), anyByte(), anyInt(), any(Http2Flags.class),
                            any(Buffer.class));

            frameReader.readFrame(ctx, input, listener);
        }
    }

    @Test
    public void failedWhenUnknownFrameInMiddleOfHeaderBlock() throws Http2Exception {
        final int streamId = 1;

        try (Buffer input = onHeapAllocator().allocate(256)) {
            Http2Headers headers = Http2Headers.newHeaders()
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
        }
    }

    @Test
    public void failedWhenContinuationFrameStreamIdMismatch() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            Http2Headers headers = Http2Headers.newHeaders()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            writeHeaderFrame(input, 1, headers,
                             new Http2Flags().endOfHeaders(false).endOfStream(true));
            writeContinuationFrame(input, 3, Http2Headers.newHeaders().add("foo", "bar"),
                    new Http2Flags().endOfHeaders(true));

            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        }
    }

    @Test
    public void failedWhenContinuationFrameNotFollowHeaderFrame() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeContinuationFrame(input, 1, Http2Headers.newHeaders().add("foo", "bar"),
                                   new Http2Flags().endOfHeaders(true));
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        }
    }

    @Test
    public void failedWhenHeaderFrameDependsOnItself() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            Http2Headers headers = Http2Headers.newHeaders()
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
        }
    }

    @Test
    public void failedHeadersValidationThrowsConnectionError() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
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
        }
    }

    @Test
    public void readHeaderAndData() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256);
             Buffer dataPayload = onHeapAllocator().allocate(256)) {
            Http2Headers headers = Http2Headers.newHeaders()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            dataPayload.writeByte((byte) 1);
            writeHeaderFrameWithData(input, 1, headers, dataPayload);

            doAnswer(invocation -> {
                assertEquals(ctx, invocation.getArgument(0));
                assertEquals(1, (int) invocation.getArgument(1));
                assertEquals(dataPayload.readerOffset(0), (Buffer) invocation.getArgument(2));
                assertEquals(0, (int) invocation.getArgument(3));
                assertTrue((boolean) invocation.getArgument(4));
                return 0;
            }).when(listener)
                    .onDataRead(any(ChannelHandlerContext.class), anyInt(), any(Buffer.class), anyInt(), anyBoolean());

            frameReader.readFrame(ctx, input, listener);

            verify(listener).onHeadersRead(ctx, 1, headers, 0, false);
        }
    }

    @Test
    public void failedWhenDataFrameNotAssociateWithStream() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256);
             Buffer payload = onHeapAllocator().allocate(256)) {
            payload.writeByte((byte) 1);

            writeFrameHeader(input, payload.readableBytes(), DATA, new Http2Flags().endOfStream(true), 0);
            input.writeBytes(payload);
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
        }
    }

    @Test
    public void readPriorityFrame() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writePriorityFrame(input, 1, 0, 10);
            frameReader.readFrame(ctx, input, listener);
        }
    }

    @Test
    public void failedWhenPriorityFrameDependsOnItself() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writePriorityFrame(input, 1, 1, 10);
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
        }
    }

    @Test
    public void failedWhenConnectionWindowUpdateFrameWithZeroDelta() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 4, WINDOW_UPDATE, new Http2Flags(), 0);
            input.writeInt(0);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        }
    }

    @Test
    public void failedWhenStreamWindowUpdateFrameWithZeroDelta() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 4, WINDOW_UPDATE, new Http2Flags(), 1);
            input.writeInt(0);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertInstanceOf(Http2Exception.StreamException.class, ex);
        }
    }

    @Test
    public void readSettingsFrame() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 6, SETTINGS, new Http2Flags(), 0);
            input.writeShort((short) SETTINGS_MAX_HEADER_LIST_SIZE);
            input.writeInt(1024);
            frameReader.readFrame(ctx, input, listener);

            listener.onSettingsRead(ctx, new Http2Settings().maxHeaderListSize(1024));
        }
    }

    @Test
    public void readAckSettingsFrame() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 0, SETTINGS, new Http2Flags().ack(true), 0);
            frameReader.readFrame(ctx, input, listener);

            listener.onSettingsAckRead(ctx);
        }
    }

    @Test
    public void failedWhenSettingsFrameOnNonZeroStream() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 6, SETTINGS, new Http2Flags(), 1);
            input.writeShort((short) SETTINGS_MAX_HEADER_LIST_SIZE);
            input.writeInt(1024);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        }
    }

    @Test
    public void failedWhenAckSettingsFrameWithPayload() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 1, SETTINGS, new Http2Flags().ack(true), 0);
            input.writeByte((byte) 1);
            Http2Exception ex = assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
            assertFalse(ex instanceof Http2Exception.StreamException);
        }
    }

    @Test
    public void failedWhenSettingsFrameWithWrongPayloadLength() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
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
        }
    }

    @Test
    public void verifyValidRequestAfterMalformedPacketCausesStreamException() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            int priorityStreamId = 3, headerStreamId = 5;
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
            Http2Headers headers = Http2Headers.newHeaders()
                    .authority("foo")
                    .method("get")
                    .path("/")
                    .scheme("https");
            Http2Flags flags = new Http2Flags().endOfHeaders(true).endOfStream(true);
            writeHeaderFrame(input, headerStreamId, headers, flags);
            frameReader.readFrame(ctx, input, listener);
            verify(listener).onHeadersRead(ctx, 5, headers, 0, true);
        }
    }

    private void writeHeaderFrame(
            Buffer output, int streamId, Http2Headers headers,
            Http2Flags flags) throws Http2Exception {
        try (Buffer headerBlock = onHeapAllocator().allocate(256)) {
            hpackEncoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), HEADERS, flags, streamId);
            output.writeBytes(headerBlock);
        }
    }

    private void writeHeaderFrameWithData(
            Buffer output, int streamId, Http2Headers headers,
            Buffer dataPayload) throws Http2Exception {
        try (Buffer headerBlock = onHeapAllocator().allocate(256)) {
            hpackEncoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), HEADERS,
                    new Http2Flags().endOfHeaders(true), streamId);
            output.writeBytes(headerBlock);

            writeFrameHeader(output, dataPayload.readableBytes(), DATA, new Http2Flags().endOfStream(true), streamId);
            output.writeBytes(dataPayload);
        }
    }

    private void writeHeaderFramePriorityPresent(
            Buffer output, int streamId, Http2Headers headers,
            Http2Flags flags, int streamDependency, int weight) throws Http2Exception {
        try (Buffer headerBlock = onHeapAllocator().allocate(256)) {
            headerBlock.writeInt(streamDependency);
            headerBlock.writeByte((byte) (weight - 1));
            hpackEncoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), HEADERS, flags, streamId);
            output.writeBytes(headerBlock);
        }
    }

    private void writeContinuationFrame(
            Buffer output, int streamId, Http2Headers headers,
            Http2Flags flags) throws Http2Exception {
        try (Buffer headerBlock = onHeapAllocator().allocate(256)) {
            hpackEncoder.encodeHeaders(streamId, headerBlock, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
            writeFrameHeader(output, headerBlock.readableBytes(), CONTINUATION, flags, streamId);
            output.writeBytes(headerBlock);
        }
    }

    private static void writePriorityFrame(
            Buffer output, int streamId, int streamDependency, int weight) {
        writeFrameHeader(output, 5, PRIORITY, new Http2Flags(), streamId);
        output.writeInt(streamDependency);
        output.writeByte((byte) (weight - 1));
    }
}
