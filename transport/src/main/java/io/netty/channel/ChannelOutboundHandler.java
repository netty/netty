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
 * {@link ChannelOperationHandler} which handles outbound data.
 */
interface ChannelOutboundHandler extends ChannelOperationHandler {
    /**
     * Return the {@link Buf} which will be used for outbound data for the given {@link ChannelHandlerContext}.
     */
    Buf newOutboundBuffer(ChannelHandlerContext ctx) throws Exception;

    /**
     * Invoked when this handler is not allowed to send any outbound message anymore and thus it's safe to
     * deallocate its outbound buffer.
     */
    void freeOutboundBuffer(ChannelHandlerContext ctx) throws Exception;
}
