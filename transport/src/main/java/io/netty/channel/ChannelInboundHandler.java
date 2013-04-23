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

import io.netty.buffer.Buf;

/**
 * {@link ChannelStateHandler} which handles inbound data.
 */
interface ChannelInboundHandler extends ChannelStateHandler {
    /**
     * Returns a new buffer which will be used to consume inbound data for the given {@link ChannelHandlerContext}.
     * <p>
     * Please note that this method can be called from any thread repeatatively, and thus you should neither perform
     * stateful operation nor keep the reference of the created buffer as a member variable.  Get it always using
     * {@link ChannelHandlerContext#inboundByteBuffer()} or {@link ChannelHandlerContext#inboundMessageBuffer()}.
     * </p>
     */
    Buf newInboundBuffer(ChannelHandlerContext ctx) throws Exception;

    /**
     * Invoked when this handler is not going to receive any inbound message anymore and thus it's safe to
     * deallocate its inbound buffer.
     * <p>
     * Please note that this method can be called from any thread repeatatively, and thus you should not perform
     * stateful operation here.
     * </p>
     */
    void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception;
}
