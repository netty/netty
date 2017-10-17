/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.channel;

import io.netty.util.internal.UnstableApi;

@UnstableApi
public final class PendingWritesTrackerFactory {

    private PendingWritesTrackerFactory() {
    }

    public static PendingWritesTracker newPendingWritesTracker(ChannelHandlerContext ctx) {
        if (ctx.pipeline() instanceof DefaultChannelPipeline) {
            final DefaultChannelPipeline pipeline = (DefaultChannelPipeline) ctx.pipeline();
            return new PendingWritesTracker() {
                @Override
                public void incrementPendingOutboundBytes(long bytes) {
                    pipeline.incrementPendingOutboundBytes(bytes);
                }

                @Override
                public void decrementPendingOutboundBytes(long bytes) {
                    pipeline.decrementPendingOutboundBytes(bytes);
                }

                @Override
                public int size(Object msg) {
                    return pipeline.estimatorHandle().size(msg);
                }
            };
        } else {
            final MessageSizeEstimator.Handle estimator = ctx.channel().config().getMessageSizeEstimator().newHandle();
            final ChannelOutboundBuffer buffer = ctx.channel().unsafe().outboundBuffer();
            if (buffer == null) {
                return new PendingWritesTracker() {
                    @Override
                    public void incrementPendingOutboundBytes(long bytes) {
                        // noop
                    }

                    @Override
                    public void decrementPendingOutboundBytes(long bytes) {
                        // noop
                    }

                    @Override
                    public int size(Object msg) {
                        return estimator.size(msg);
                    }
                };
            } else {
                return new PendingWritesTracker() {
                    @Override
                    public void incrementPendingOutboundBytes(long bytes) {
                        // We need to guard against null as channel.unsafe().outboundBuffer() may returned null
                        // if the channel was already closed when constructing the PendingWriteQueue.
                        // See https://github.com/netty/netty/issues/3967
                        if (buffer != null) {
                            buffer.incrementPendingOutboundBytes(bytes);
                        }
                    }

                    @Override
                    public void decrementPendingOutboundBytes(long bytes) {
                        // We need to guard against null as channel.unsafe().outboundBuffer() may returned null
                        // if the channel was already closed when constructing the PendingWriteQueue.
                        // See https://github.com/netty/netty/issues/3967
                        if (buffer != null) {
                            buffer.decrementPendingOutboundBytes(bytes);
                        }
                    }

                    @Override
                    public int size(Object msg) {
                        return estimator.size(msg);
                    }
                };
            }
        }
    }

    @UnstableApi
    public interface PendingWritesTracker extends MessageSizeEstimator.Handle {
        void incrementPendingOutboundBytes(long bytes);
        void decrementPendingOutboundBytes(long bytes);
    }

}
