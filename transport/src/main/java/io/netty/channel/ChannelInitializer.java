/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

@Sharable
public abstract class ChannelInitializer<C extends Channel> extends ChannelStateHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelInitializer.class);

    public abstract void initChannel(C ch) throws Exception;

    @SuppressWarnings("unchecked")
    @Override
    public final void channelRegistered(ChannelHandlerContext ctx)
            throws Exception {
        boolean removed = false;
        boolean success = false;
        try {
            initChannel((C) ctx.channel());
            ctx.pipeline().remove(this);
            removed = true;
            ctx.fireChannelRegistered();
            success = true;
        } catch (Throwable t) {
            logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), t);
        } finally {
            if (!removed) {
                ctx.pipeline().remove(this);
            }
            if (!success) {
                ctx.close();
            }
        }
    }
}
