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
package io.netty5.handler.codec.http2;

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FutureListener;
import static java.util.Objects.requireNonNull;

import io.netty5.util.internal.ObjectUtil;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;

/**
 * {@link DecoratingHttp2ConnectionEncoder} which guards against a remote peer that will trigger a massive amount
 * of control frames but will not consume our responses to these.
 * This encoder will tear-down the connection once we reached the configured limit to reduce the risk of DDOS.
 */
final class Http2ControlFrameLimitEncoder extends DecoratingHttp2ConnectionEncoder {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Http2ControlFrameLimitEncoder.class);

    private final int maxOutstandingControlFrames;
    private Http2LifecycleManager lifecycleManager;
    private int outstandingControlFrames;
    private final FutureListener<Void> outstandingControlFramesListener = future -> outstandingControlFrames--;
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
    public Future<Void> writeSettingsAck(ChannelHandlerContext ctx) {
        FutureListener<Void> listener = handleOutstandingControlFrames(ctx);
        Future<Void> f = super.writeSettingsAck(ctx);
        if (listener != null) {
            f.addListener(listener);
        }
        return f;
    }

    @Override
    public Future<Void> writePing(ChannelHandlerContext ctx, boolean ack, long data) {
        // Only apply the limit to ping acks.
        if (ack) {
            FutureListener<Void> listener = handleOutstandingControlFrames(ctx);
            Future<Void> f = super.writePing(ctx, ack, data);
            if (listener != null) {
                f.addListener(listener);
            }
            return f;
        }
        return super.writePing(ctx, ack, data);
    }

    @Override
    public Future<Void> writeRstStream(
            ChannelHandlerContext ctx, int streamId, long errorCode) {
        FutureListener<Void> listener = handleOutstandingControlFrames(ctx);
        Future<Void> f = super.writeRstStream(ctx, streamId, errorCode);
        if (listener != null) {
            f.addListener(listener);
        }
        return f;
    }

    private FutureListener<Void> handleOutstandingControlFrames(ChannelHandlerContext ctx) {
        if (!limitReached) {
            if (outstandingControlFrames == maxOutstandingControlFrames) {
                // Let's try to flush once as we may be able to flush some of the control frames.
                ctx.flush();
            }
            if (outstandingControlFrames == maxOutstandingControlFrames) {
                limitReached = true;
                Http2Exception exception = Http2Exception.connectionError(Http2Error.ENHANCE_YOUR_CALM,
                        "Maximum number %d of outstanding control frames reached", maxOutstandingControlFrames);
                logger.info("Maximum number {} of outstanding control frames reached. Closing channel {}",
                        maxOutstandingControlFrames, ctx.channel(), exception);

                // First notify the Http2LifecycleManager and then close the connection.
                lifecycleManager.onError(ctx, true, exception);
                ctx.close();
                return null;
            }
            outstandingControlFrames++;

            // We did not reach the limit yet, add the listener to decrement the number of outstanding control frames
            // once the promise was completed
            return outstandingControlFramesListener;
        }
        return null;
    }
}
