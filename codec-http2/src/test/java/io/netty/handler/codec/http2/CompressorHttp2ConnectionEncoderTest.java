/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompressorHttp2ConnectionEncoderTest {
    private static final int STREAM_ID = 3;

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void compressorIsClosedWhenHeadersWriteDoesNotCreateStream(boolean hasPriority) {
        Http2Connection connection = new DefaultHttp2Connection(false);
        DefaultHttp2ConnectionEncoder delegate = newDelegate(connection, mock(Http2FrameWriter.class));
        TestCompressorHttp2ConnectionEncoder encoder = new TestCompressorHttp2ConnectionEncoder(delegate);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Promise<Void> promise = newPromise();

        writeHeaders(encoder, ctx, 2, promise, hasPriority);

        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        assertNull(connection.stream(2));
        assertClosed(encoder.compressor);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void compressorIsClosedWhenDelegateThrows(boolean hasPriority) {
        RuntimeException cause = new RuntimeException("write failed");
        Http2Connection connection = new DefaultHttp2Connection(false);
        Http2ConnectionEncoder delegate = mock(Http2ConnectionEncoder.class);
        when(delegate.connection()).thenReturn(connection);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Promise<Void> promise = newPromise();
        failHeadersWrite(delegate, ctx, promise, hasPriority, cause);
        TestCompressorHttp2ConnectionEncoder encoder = new TestCompressorHttp2ConnectionEncoder(delegate);

        writeHeaders(encoder, ctx, STREAM_ID, promise, hasPriority);

        assertSame(cause, promise.cause());
        assertClosed(encoder.compressor);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void streamOwnsCompressorWhenHeadersWriteFailsAsynchronously(boolean hasPriority) throws Exception {
        RuntimeException cause = new RuntimeException("write failed");
        Http2Connection connection = new DefaultHttp2Connection(false);
        Http2FrameWriter writer = mock(Http2FrameWriter.class);
        DefaultHttp2ConnectionEncoder delegate = newDelegate(connection, writer);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Promise<Void> promise = newPromise();
        TestCompressorHttp2ConnectionEncoder encoder = new TestCompressorHttp2ConnectionEncoder(delegate);

        writeHeaders(encoder, ctx, STREAM_ID, promise, hasPriority);

        Http2Stream stream = connection.stream(STREAM_ID);
        assertFalse(promise.isDone());
        assertTrue(encoder.compressor.isOpen());

        promise.setFailure(cause);
        assertTrue(encoder.compressor.isOpen());

        stream.close();
        assertClosed(encoder.compressor);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void existingStreamOwnsCompressorWhenHeadersWriteFails(boolean hasPriority) throws Exception {
        Http2Connection connection = new DefaultHttp2Connection(false);
        Http2Stream stream = connection.local().createStream(STREAM_ID, true);
        DefaultHttp2ConnectionEncoder delegate = newDelegate(connection, mock(Http2FrameWriter.class));
        TestCompressorHttp2ConnectionEncoder encoder = new TestCompressorHttp2ConnectionEncoder(delegate);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Promise<Void> promise = newPromise();

        writeHeaders(encoder, ctx, STREAM_ID, promise, hasPriority);

        assertTrue(promise.isDone());
        assertFalse(promise.isSuccess());
        assertTrue(encoder.compressor.isOpen());

        stream.close();
        assertClosed(encoder.compressor);
    }

    @Test
    public void compressorIsClosedWhenTargetContentEncodingFails() {
        RuntimeException cause = new RuntimeException("encoding failed");
        Http2ConnectionEncoder delegate = mock(Http2ConnectionEncoder.class);
        when(delegate.connection()).thenReturn(new DefaultHttp2Connection(false));
        TestCompressorHttp2ConnectionEncoder encoder = new TestCompressorHttp2ConnectionEncoder(delegate);
        encoder.targetContentEncodingFailure = cause;
        Promise<Void> promise = newPromise();

        writeHeaders(encoder, mock(ChannelHandlerContext.class), STREAM_ID, promise, false);

        assertSame(cause, promise.cause());
        assertClosed(encoder.compressor);
    }

    private static void writeHeaders(Http2ConnectionEncoder encoder, ChannelHandlerContext ctx, int streamId,
                                     Promise<Void> promise, boolean hasPriority) {
        Http2Headers headers = new DefaultHttp2Headers().set(CONTENT_ENCODING, GZIP);
        if (hasPriority) {
            encoder.writeHeaders(ctx, streamId, headers, 0, (short) 16, false, 0, false, promise);
        } else {
            encoder.writeHeaders(ctx, streamId, headers, 0, false, promise);
        }
    }

    private static void failHeadersWrite(Http2ConnectionEncoder delegate, ChannelHandlerContext ctx,
                                         Promise<Void> promise, boolean hasPriority, RuntimeException cause) {
        if (hasPriority) {
            doThrow(cause).when(delegate).writeHeaders(eq(ctx), eq(STREAM_ID), any(Http2Headers.class), eq(0),
                    eq((short) 16), eq(false), eq(0), eq(false), eq(promise));
        } else {
            doThrow(cause).when(delegate).writeHeaders(eq(ctx), eq(STREAM_ID), any(Http2Headers.class), eq(0),
                    eq(false), eq(promise));
        }
    }

    private static Promise<Void> newPromise() {
        return ImmediateEventExecutor.INSTANCE.newPromise();
    }

    private static DefaultHttp2ConnectionEncoder newDelegate(Http2Connection connection, Http2FrameWriter writer) {
        connection.remote().flowController(mock(Http2RemoteFlowController.class));
        DefaultHttp2ConnectionEncoder delegate = new DefaultHttp2ConnectionEncoder(connection, writer);
        delegate.lifecycleManager(mock(Http2LifecycleManager.class));
        return delegate;
    }

    private static void assertClosed(TestEmbeddedChannel compressor) {
        assertFalse(compressor.isOpen());
        assertEquals(1, compressor.cleanupCalls);
    }

    private static final class TestCompressorHttp2ConnectionEncoder extends CompressorHttp2ConnectionEncoder {
        TestEmbeddedChannel compressor;
        RuntimeException targetContentEncodingFailure;

        TestCompressorHttp2ConnectionEncoder(Http2ConnectionEncoder delegate) {
            super(delegate);
        }

        @Override
        protected EmbeddedChannel newContentCompressor(ChannelHandlerContext ctx, CharSequence contentEncoding) {
            compressor = new TestEmbeddedChannel();
            return compressor;
        }

        @Override
        protected CharSequence getTargetContentEncoding(CharSequence contentEncoding) throws Http2Exception {
            if (targetContentEncodingFailure != null) {
                throw targetContentEncodingFailure;
            }
            return contentEncoding;
        }
    }

    private static final class TestEmbeddedChannel extends EmbeddedChannel {
        int cleanupCalls;

        @Override
        public boolean finishAndReleaseAll() throws Exception {
            ++cleanupCalls;
            return super.finishAndReleaseAll();
        }
    }
}
