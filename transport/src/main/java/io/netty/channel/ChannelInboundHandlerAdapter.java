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
 * Abstract base class for a {@link ChannelHandler} that handles inbound data.
 *
 * Most of the times you either want to extend {@link ChannelInboundByteHandlerAdapter} or
 * {@link ChannelInboundMessageHandlerAdapter}.
 */
public abstract class ChannelInboundHandlerAdapter
        extends ChannelStateHandlerAdapter implements ChannelInboundHandler {

    /**
     * Calls {@link Buf#free()} to free the buffer, sub-classes may override this.
     *
     * When doing so be aware that you will need to handle all the resource management by your own.
     */
    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx, Buf buf) throws Exception {
        buf.free();
    }
}
