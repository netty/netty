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

import java.net.SocketAddress;

public interface ChannelOutboundHandler<T> extends ChannelHandler {
    ChannelBufferHolder<T> newOutboundBuffer(ChannelOutboundHandlerContext<T> ctx) throws Exception;

    void bind(ChannelOutboundHandlerContext<T> ctx, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void connect(ChannelOutboundHandlerContext<T> ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future) throws Exception;
    void disconnect(ChannelOutboundHandlerContext<T> ctx, ChannelFuture future) throws Exception;
    void close(ChannelOutboundHandlerContext<T> ctx, ChannelFuture future) throws Exception;
    void deregister(ChannelOutboundHandlerContext<T> ctx, ChannelFuture future) throws Exception;
    void flush(ChannelOutboundHandlerContext<T> ctx, ChannelFuture future) throws Exception;
}
