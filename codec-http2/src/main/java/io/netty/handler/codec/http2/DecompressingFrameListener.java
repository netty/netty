/*
 * Copyright 2025 The Netty Project
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
import io.netty.handler.codec.compression.BackpressureGauge;
import io.netty.handler.codec.compression.Decompressor;
import io.netty.handler.codec.http.HttpDecompressionHandler;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.RecyclableArrayList;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.IDENTITY;

public class DecompressingFrameListener extends Http2FrameListenerDecorator {
    private static final Object EOF = new Object();

    private final BackpressureGauge.Builder backpressureGaugeBuilder;
    private final HttpDecompressionHandler.DecompressionDecider decompressionDecider;
    private final Http2Connection connection;
    private final Http2Connection.PropertyKey propertyKey;
    private Http2LocalFlowController upstreamFlowController;

    DecompressingFrameListener(Builder builder, Http2Connection connection, Http2FrameListener delegate) {
        super(delegate);
        this.backpressureGaugeBuilder = builder.backpressureGaugeBuilder;
        this.decompressionDecider = builder.decompressionDecider;
        this.connection = connection;
        this.propertyKey = connection.newKey();

        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamRemoved(Http2Stream stream) {
                StreamHolder holder = holder(stream);
                if (holder != null) {
                    holder.close();
                }
            }
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    private StreamHolder holder(Http2Stream stream) {
        return stream == null ? null : stream.getProperty(propertyKey);
    }

    @Override
    public void onHeadersRead(
            ChannelHandlerContext ctx,
            int streamId,
            Http2Headers headers,
            int padding,
            boolean endStream) throws Http2Exception {

        initDecompressor(ctx, streamId, headers, endStream);
        super.onHeadersRead(ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void onHeadersRead(
            ChannelHandlerContext ctx,
            int streamId,
            Http2Headers headers,
            int streamDependency,
            short weight,
            boolean exclusive,
            int padding,
            boolean endStream) throws Http2Exception {

        initDecompressor(ctx, streamId, headers, endStream);
        super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
    }

    private void initDecompressor(ChannelHandlerContext ctx, int streamId, Http2Headers headers, boolean endOfStream)
            throws Http2Exception {
        final Http2Stream stream = connection.stream(streamId);
        if (stream == null) {
            return;
        }

        StreamHolder decompressor = holder(stream);
        if (decompressor == null && !endOfStream) {
            // Determine the content encoding.
            CharSequence contentEncoding = headers.get(CONTENT_ENCODING);
            if (contentEncoding == null) {
                contentEncoding = IDENTITY;
            }
            try {
                Decompressor.AbstractDecompressorBuilder decompressorBuilder =
                        decompressionDecider.newDecompressorBuilder(contentEncoding.toString());
                if (decompressorBuilder != null) {
                    decompressor = new StreamHolder(ctx, decompressorBuilder.build(ctx.alloc()));
                    stream.setProperty(propertyKey, decompressor);
                    // Decode the content and remove or replace the existing headers
                    // so that the message looks like a decoded message.
                    CharSequence targetContentEncoding = decompressionDecider.getTargetContentEncoding(contentEncoding);
                    if (IDENTITY.contentEqualsIgnoreCase(targetContentEncoding)) {
                        headers.remove(CONTENT_ENCODING);
                    } else {
                        headers.set(CONTENT_ENCODING, targetContentEncoding);
                    }
                }
            } catch (Http2Exception e) {
                throw e;
            } catch (Exception e) {
                throw new Http2Exception(Http2Error.INTERNAL_ERROR, "Failed to create decompressor", e);
            }
        }

        if (decompressor != null) {
            // The content length will be for the compressed data. Since we will decompress the data
            // this content-length will not be correct. Instead of queuing messages or delaying sending
            // header frames just remove the content-length header.
            headers.remove(CONTENT_LENGTH);

            // The first time that we initialize a decompressor, decorate the local flow controller to
            // properly convert consumed bytes.
            if (upstreamFlowController == null) {
                upstreamFlowController = connection.local().flowController();
                connection.local().flowController(new DecompressorFlowController());
            }
        }
    }

    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        Http2Stream stream = connection.stream(streamId);
        if (stream != null) {
            StreamHolder holder = holder(stream);
            if (holder != null) {
                return holder.onDataRead(stream, data, padding, endOfStream);
            }
        }
        return super.onDataRead(ctx, streamId, data, padding, endOfStream);
    }

    private class StreamHolder {
        private final ChannelHandlerContext ctx;
        private final Decompressor decompressor;

        private boolean working;

        private RecyclableArrayList inboundQueue = RecyclableArrayList.newInstance();
        private final BackpressureGauge backpressureGauge = backpressureGaugeBuilder.build();

        StreamHolder(ChannelHandlerContext ctx, Decompressor decompressor) {
            this.ctx = ctx;
            this.decompressor = decompressor;

            backpressureGauge.relieveBackpressure();
        }

        boolean consumeBytes(Http2Stream stream, int numBytes) throws Http2Exception {
            backpressureGauge.relieveBackpressure(numBytes);
            int consumedInput = work(stream);
            return consumedInput != 0 && upstreamFlowController.consumeBytes(stream, consumedInput);
        }

        int onDataRead(Http2Stream stream, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            inboundQueue.add(data.retain());
            if (endOfStream) {
                inboundQueue.add(EOF);
            }
            return work(stream);
        }

        private int work(Http2Stream stream) throws Http2Exception {
            // prevent reentrancy
            if (working) {
                return 0;
            }
            working = true;
            int consumedInput = 0;
            try {
                while (true) {
                    Decompressor.Status status = decompressor.status();
                    switch (status) {
                        case NEED_INPUT:
                            if (inboundQueue.isEmpty()) {
                                return consumedInput;
                            }

                            Object item = inboundQueue.remove(0);
                            if (item == EOF) {
                                decompressor.endOfInput();
                                break;
                            }
                            ByteBuf buffer = (ByteBuf) item;
                            // prevent overflow
                            if (consumedInput + buffer.readableBytes() < consumedInput) {
                                inboundQueue.add(0, item);
                                return consumedInput;
                            }
                            consumedInput += buffer.readableBytes();
                            decompressor.addInput(buffer);
                            break;
                        case NEED_OUTPUT:
                            if (backpressureGauge.backpressureLimitExceeded()) {
                                return consumedInput;
                            }
                            ByteBuf output = decompressor.takeOutput();
                            try {
                                backpressureGauge.countMessage(output.readableBytes());
                                int n = listener.onDataRead(ctx, stream.id(), output, 0, false);
                                if (n > 0) {
                                    backpressureGauge.relieveBackpressure(n);
                                }
                            } finally {
                                output.release();
                            }
                            break;
                        case COMPLETE:
                            listener.onDataRead(ctx, stream.id(), Unpooled.EMPTY_BUFFER, 0, true);
                            return consumedInput;
                    }
                }
            } finally {
                working = false;
            }
        }

        void close() {
            decompressor.close();
            if (inboundQueue != null) {
                for (Object o : inboundQueue) {
                    if (o != EOF) {
                        ((ByteBuf) o).release();
                    }
                }
                inboundQueue.recycle();
                inboundQueue = null;
            }
        }
    }

    private class DecompressorFlowController implements Http2LocalFlowController {

        @Override
        public Http2LocalFlowController frameWriter(Http2FrameWriter frameWriter) {
            return upstreamFlowController.frameWriter(frameWriter);
        }

        @Override
        public void receiveFlowControlledFrame(
                Http2Stream stream, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            upstreamFlowController.receiveFlowControlledFrame(stream, data, padding, endOfStream);
        }

        @Override
        public boolean consumeBytes(Http2Stream stream, int numBytes) throws Http2Exception {
            StreamHolder holder = holder(stream);
            if (holder == null) {
                return upstreamFlowController.consumeBytes(stream, numBytes);
            } else {
                return holder.consumeBytes(stream, numBytes);
            }
        }

        @Override
        public int unconsumedBytes(Http2Stream stream) {
            return upstreamFlowController.unconsumedBytes(stream);
        }

        @Override
        public int initialWindowSize(Http2Stream stream) {
            return upstreamFlowController.initialWindowSize(stream);
        }

        @Override
        public void channelHandlerContext(ChannelHandlerContext ctx) throws Http2Exception {
            upstreamFlowController.channelHandlerContext(ctx);
        }

        @Override
        public void initialWindowSize(int newWindowSize) throws Http2Exception {
            upstreamFlowController.initialWindowSize(newWindowSize);
        }

        @Override
        public int initialWindowSize() {
            return upstreamFlowController.initialWindowSize();
        }

        @Override
        public int windowSize(Http2Stream stream) {
            return upstreamFlowController.windowSize(stream);
        }

        @Override
        public void incrementWindowSize(Http2Stream stream, int delta) throws Http2Exception {
            upstreamFlowController.incrementWindowSize(stream, delta);
        }
    }

    public static final class Builder {
        HttpDecompressionHandler.DecompressionDecider decompressionDecider =
                HttpDecompressionHandler.DecompressionDecider.DEFAULT;
        BackpressureGauge.Builder backpressureGaugeBuilder = BackpressureGauge.builder();

        public Builder decompressionDecider(HttpDecompressionHandler.DecompressionDecider decompressionDecider) {
            this.decompressionDecider = ObjectUtil.checkNotNull(decompressionDecider, "decompressionDecider");
            return this;
        }

        public Builder backpressureGaugeBuilder(BackpressureGauge.Builder backpressureGaugeBuilder) {
            this.backpressureGaugeBuilder =
                    ObjectUtil.checkNotNull(backpressureGaugeBuilder, "backpressureGaugeBuilder");
            return this;
        }

        public Http2FrameListener build(Http2Connection connection, Http2FrameListener delegate) {
            return new DecompressingFrameListener(this, connection, delegate);
        }
    }
}
