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

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.http2.Http2CodecUtil.SETTINGS_MAX_HEADER_LIST_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.writeFrameHeader;
import static io.netty5.handler.codec.http2.Http2FrameTypes.CONTINUATION;
import static io.netty5.handler.codec.http2.Http2FrameTypes.DATA;
import static io.netty5.handler.codec.http2.Http2FrameTypes.HEADERS;
import static io.netty5.handler.codec.http2.Http2FrameTypes.PRIORITY;
import static io.netty5.handler.codec.http2.Http2FrameTypes.SETTINGS;
import static io.netty5.handler.codec.http2.Http2FrameTypes.WINDOW_UPDATE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


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

        when(ctx.bufferAllocator()).thenReturn(onHeapAllocator());

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
            frameReader.readFrame(ctx, input, listener);

            verify(listener).onUnknownFrame(
                    ctx, (byte) 0xff, 0, new Http2Flags(), payload.readerOffset(0));
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

            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
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

            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
        }
    }

    @Test
    public void failedWhenContinuationFrameNotFollowHeaderFrame() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeContinuationFrame(input, 1, Http2Headers.newHeaders().add("foo", "bar"),
                                   new Http2Flags().endOfHeaders(true));
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
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
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
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

            frameReader.readFrame(ctx, input, listener);

            verify(listener).onHeadersRead(ctx, 1, headers, 0, false);
            verify(listener).onDataRead(ctx, 1, dataPayload.readerOffset(0), 0, true);
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
    public void failedWhenWindowUpdateFrameWithZeroDelta() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 4, WINDOW_UPDATE, new Http2Flags(), 0);
            input.writeInt(0);
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
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
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
        }
    }

    @Test
    public void failedWhenAckSettingsFrameWithPayload() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 1, SETTINGS, new Http2Flags().ack(true), 0);
            input.writeByte((byte) 1);
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
        }
    }

    @Test
    public void failedWhenSettingsFrameWithWrongPayloadLength() throws Http2Exception {
        try (Buffer input = onHeapAllocator().allocate(256)) {
            writeFrameHeader(input, 8, SETTINGS, new Http2Flags(), 0);
            input.writeInt(SETTINGS_MAX_HEADER_LIST_SIZE);
            input.writeInt(1024);
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    frameReader.readFrame(ctx, input, listener);
                }
            });
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
