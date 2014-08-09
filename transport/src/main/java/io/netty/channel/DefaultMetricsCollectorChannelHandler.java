/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.metrics.DefaultMetricsCollector;

public class DefaultMetricsCollectorChannelHandler extends ChannelHandlerAdapter {
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        DefaultMetricsCollector metrics = (DefaultMetricsCollector) ctx.channel().eventLoop().metrics();
        metrics.registerChannel();

        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        DefaultMetricsCollector metrics = (DefaultMetricsCollector) ctx.channel().eventLoop().metrics();
        metrics.unregisterChannel();

        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        DefaultMetricsCollector metrics = (DefaultMetricsCollector) ctx.channel().eventLoop().metrics();
        metrics.readBytes(((ByteBuf) msg).readableBytes());

        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        DefaultMetricsCollector metrics = (DefaultMetricsCollector) ctx.channel().eventLoop().metrics();
        metrics.writeBytes(((ByteBuf) msg).readableBytes());

        ctx.write(msg, promise);
    }
}
