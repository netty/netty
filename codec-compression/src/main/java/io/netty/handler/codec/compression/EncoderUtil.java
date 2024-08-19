/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;

import java.util.concurrent.TimeUnit;

final class EncoderUtil {
    private static final int THREAD_POOL_DELAY_SECONDS = 10;

    static void closeAfterFinishEncode(final ChannelHandlerContext ctx, final ChannelFuture finishFuture,
                                       final ChannelPromise promise) {
        if (!finishFuture.isDone()) {
            // Ensure the channel is closed even if the write operation completes in time.
            final Future<?> future = ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    ctx.close(promise);
                }
            }, THREAD_POOL_DELAY_SECONDS, TimeUnit.SECONDS);

            finishFuture.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f)  {
                    // Cancel the scheduled timeout.
                    future.cancel(true);
                    if (!promise.isDone()) {
                        ctx.close(promise);
                    }
                }
            });
        } else {
            ctx.close(promise);
        }
    }

    private EncoderUtil() { }
}

