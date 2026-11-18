/*
 * Copyright 2026 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DelegatingDecompressorFrameListenerTest {

    private static final int STREAM_ID = 3;

    private EmbeddedChannel channel;
    private ChannelHandlerContext ctx;
    private DefaultHttp2Connection connection;
    private DelegatingDecompressorFrameListener listener;
    private final StringBuilder received = new StringBuilder();
    private int endOfStreamCount;
    private Throwable removalFailure;

    @BeforeEach
    public void setUp() throws Exception {
        channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ctx = channel.pipeline().firstContext();
        connection = new DefaultHttp2Connection(true) {
            @Override
            public void addListener(final Listener l) {
                if (l.getClass().getEnclosingClass() != DelegatingDecompressorFrameListener.class) {
                    super.addListener(l);
                    return;
                }
                // DefaultHttp2Connection logs and swallows exceptions of listeners, record them instead.
                super.addListener(new Http2ConnectionAdapter() {
                    @Override
                    public void onStreamRemoved(Http2Stream stream) {
                        try {
                            l.onStreamRemoved(stream);
                        } catch (Throwable cause) {
                            removalFailure = cause;
                        }
                    }
                });
            }
        };
        connection.local().flowController(new DefaultHttp2LocalFlowController(connection));
        // The flow controller requires the context to be set before bytes can be returned to it.
        connection.local().flowController().channelHandlerContext(ctx);
        listener = new DelegatingDecompressorFrameListener(connection, new Http2FrameAdapter() {
            @Override
            public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                                  boolean endOfStream) {
                received.append(data.toString(CharsetUtil.US_ASCII));
                if (endOfStream) {
                    endOfStreamCount++;
                }
                // No flow control window was reserved for these bytes in this test, so don't return any.
                return 0;
            }
        });
        connection.remote().createStream(STREAM_ID, false);
        Http2Headers headers = new DefaultHttp2Headers().set("content-encoding", "gzip");
        listener.onHeadersRead(ctx, STREAM_ID, headers, 0, false);
    }

    @AfterEach
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Test
    public void completeGzipStreamIsReportedAsEndOfStream() throws Exception {
        byte[] gz = gzip("hello, world");

        listener.onDataRead(ctx, STREAM_ID, Unpooled.wrappedBuffer(gz), 0, true);

        assertThat(received.toString()).isEqualTo("hello, world");
        assertThat(endOfStreamCount).isEqualTo(1);
    }

    @Test
    public void truncatedGzipStreamIsNotReportedAsEndOfStream() throws Exception {
        byte[] gz = gzip("hello, world");
        // drop the CRC32 and ISIZE footer
        final byte[] truncated = Arrays.copyOf(gz, gz.length - 8);

        assertThatThrownBy(() -> listener.onDataRead(ctx, STREAM_ID, Unpooled.wrappedBuffer(truncated), 0, true))
                .isInstanceOf(Http2Exception.StreamException.class);

        // The data that could be decoded was delivered, but the application must never see the end of the stream.
        assertThat(received.toString()).isEqualTo("hello, world");
        assertThat(endOfStreamCount).isZero();
    }

    @Test
    public void removingStreamWithTruncatedGzipStreamDoesNotFail() throws Exception {
        byte[] gz = gzip("hello, world");
        // drop the CRC32 and ISIZE footer, the stream is then reset before the data was complete
        byte[] truncated = Arrays.copyOf(gz, gz.length - 8);

        listener.onDataRead(ctx, STREAM_ID, Unpooled.wrappedBuffer(truncated), 0, false);
        connection.stream(STREAM_ID).close();

        assertThat(connection.stream(STREAM_ID)).isNull();
        assertThat(removalFailure).isNull();
        assertThat(endOfStreamCount).isZero();
    }

    private static byte[] gzip(String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GZIPOutputStream out = new GZIPOutputStream(bytes);
        out.write(text.getBytes(CharsetUtil.US_ASCII));
        out.close();
        return bytes.toByteArray();
    }
}
