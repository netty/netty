/*
 * Copyright 2019 The Netty Project
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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * {@link DecoratingHttp2ConnectionEncoder} which guards against a remote peer that will trigger a massive amount
 * of control frames but will not consume our responses to these.
 * This encoder will tear-down the connection once we reached the configured limit to reduce the risk of DDOS.
 */
final class Http2ControlFrameLimitEncoder extends DecoratingHttp2ConnectionEncoder {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http2ControlFrameLimitEncoder.class);

    private final int maxOutstandingControlFrames;
    private final ChannelFutureListener outstandingControlFramesListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            outstandingControlFrames--;
        }
    };
    private Http2LifecycleManager lifecycleManager;
    private int outstandingControlFrames;
    private boolean limitReached;

    Http2ControlFrameLimitEncoder(Http2ConnectionEncoder delegate, int maxOutstandingControlFrames) {
        super(delegate);
        this.maxOutstandingControlFrames = ObjectUtil.checkPositive(maxOutstandingControlFrames,
                "maxOutstandingControlFrames");
    }

    @Override
    public void lifecycleManager(Http2LifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
        super.lifecycleManager(lifecycleManager);
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        ChannelPromise newPromise = handleOutstandingControlFrames(ctx, promise);
        if (newPromise == null) {
            return promise;
        }
        return super.writeSettingsAck(ctx, newPromise);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, long data, ChannelPromise promise) {
        // Only apply the limit to ping acks.
        if (ack) {
            ChannelPromise newPromise = handleOutstandingControlFrames(ctx, promise);
            if (newPromise == null) {
                return promise;
            }
            return super.writePing(ctx, ack, data, newPromise);
        }
        return super.writePing(ctx, ack, data, promise);
    }

    @Override
    public ChannelFuture writeRstStream(
            ChannelHandlerContext ctx, int streamId, long errorCode, ChannelPromise promise) {
        ChannelPromise newPromise = handleOutstandingControlFrames(ctx, promise);
        if (newPromise == null) {
            return promise;
        }
        return super.writeRstStream(ctx, streamId, errorCode, newPromise);
    }

    private ChannelPromise handleOutstandingControlFrames(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (!limitReached) {
            if (outstandingControlFrames == maxOutstandingControlFrames) {
                // Let's try to flush once as we may be able to flush some of the control frames.
                ctx.flush();
            }
            if (outstandingControlFrames == maxOutstandingControlFrames) {
                limitReached = true;
                Http2Exception exception = Http2Exception.connectionError(Http2Error.ENHANCE_YOUR_CALM,
                        "Maximum number %d of outstanding control frames reached", maxOutstandingControlFrames);
                logger.info("{} Maximum number {} of outstanding control frames reached, closing channel.",
                        ctx.channel(), maxOutstandingControlFrames, exception);

                // First notify the Http2LifecycleManager and then close the connection.
                lifecycleManager.onError(ctx, true, exception);
                ctx.close();
            }
            outstandingControlFrames++;

            // We did not reach the limit yet, add the listener to decrement the number of outstanding control frames
            // once the promise was completed
            return promise.unvoid().addListener(outstandingControlFramesListener);
        }
        return promise;
    }
}
