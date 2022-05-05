/*
 * Copyright 2021 The Netty Project
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
package io.netty5.handler.codec.h2new;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.h2new.Http2ControlStreamInitializer.ControlStream;
import io.netty5.util.internal.EmptyArrays;

import java.util.function.Supplier;

import static io.netty5.handler.codec.h2new.Http2ControlStreamInitializer.CONTROL_STREAM_ATTRIBUTE_KEY;
import static io.netty5.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;

/**
 * A {@link ChannelHandler} that manages <a href="https://httpwg.org/specs/rfc7540.html#GOAWAY">GOAWAY</a> handling. An
 * intent to send a {@link Http2GoAwayFrame} can be indicated by triggering a {@link SendGoAwayEvent}. <p>
 * This handler <b>must</b> be added on the parent {@link Http2Channel} such that it can
 * {@link #channelRead(ChannelHandlerContext, Object) see} all {@link Http2Frame}s read on the channel.
 *
 */
final class GoAwayManager extends ChannelHandlerAdapter {
    private BufferAllocator allocator;
    private Supplier<Buffer> emptyDebugDataSupplier;
    private int lastStreamId;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        final BufferAllocator alloc = ctx.bufferAllocator();
        if (!alloc.getAllocationType().isDirect()) {
            allocator = alloc;
        } else {
            allocator = DefaultBufferAllocators.onHeapAllocator();
        }
        emptyDebugDataSupplier = allocator.constBufferSupplier(EmptyArrays.EMPTY_BYTES);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (allocator != null) {
            allocator.close();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2Frame) {
            int streamId = ((Http2Frame) msg).streamId();
            if (streamId > lastStreamId) {
                lastStreamId = streamId;
            }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SendGoAwayEvent) {
            SendGoAwayEvent sendGoAwayEvent = (SendGoAwayEvent) evt;
            // TODO: check if go_away already sent?
            final ControlStream controlStream = ctx.channel().attr(CONTROL_STREAM_ATTRIBUTE_KEY).get();
            Channel controlChannel;
            if (controlStream == null) {
                // raw channel
                controlChannel = ctx.channel();
                ctx.fireUserEventTriggered(sendGoAwayEvent);
            } else {
                controlChannel = controlStream.channel();
                controlChannel.pipeline().fireUserEventTriggered(sendGoAwayEvent);
            }
            Buffer debugData = sendGoAwayEvent.debugData();
            if (debugData == null) {
                debugData = emptyDebugDataSupplier.get();
            }
            controlChannel.writeAndFlush(new DefaultHttp2GoAwayFrame(lastStreamId, sendGoAwayEvent.errorCode(),
                    debugData));
            return;
        }
        ctx.fireUserEventTriggered(evt);
    }

    /**
     * An event to indicate an intent of sending a {@link Http2GoAwayFrame} to the peer. It is up to the receiver of
     * this event to determine whether sending that frame is required.
     */
    interface SendGoAwayEvent {

        /**
         * Returns the {@link Http2GoAwayFrame#errorCode() error code} for the {@link Http2GoAwayFrame}.
         *
         * @return The error code for the {@link Http2GoAwayFrame}.
         */
        long errorCode();

        /**
         * Returns the {@link Http2GoAwayFrame#debugData()} debug data} for the {@link Http2GoAwayFrame}, {@code null}
         * if none exists.
         *
         * @return The {@link Http2GoAwayFrame#debugData()} debug data} for the {@link Http2GoAwayFrame}, {@code null}
         * if none exists.
         */
        Buffer debugData();
    }

    static final class ProtocolErrorEvent implements SendGoAwayEvent {
        static final ProtocolErrorEvent PROTOCOL_ERROR_EVENT_NO_DEBUG_DATA = new ProtocolErrorEvent();
        private final Buffer debugData;

        private ProtocolErrorEvent() {
            debugData = null;
        }

        /**
         * Creates a new instance.
         *
         * @param debugData {@link #debugData()} for this event, must be non-null.
         */
        ProtocolErrorEvent(Buffer debugData) {
            this.debugData = checkNotNullWithIAE(debugData, "debugData");
        }

        @Override
        public long errorCode() {
            return PROTOCOL_ERROR.code();
        }

        @Override
        public Buffer debugData() {
            return debugData;
        }
    }
}
