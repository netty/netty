/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.discard;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundStreamHandlerAdapter;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles a server-side channel.
 */
public class DiscardServerHandler extends ChannelInboundStreamHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            DiscardServerHandler.class.getName());


    @Override
    public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx, ChannelBuffer in)
            throws Exception {
        // Discard the received data silently.
        in.clear();
    }


    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<Byte> ctx,
            Throwable cause) throws Exception {
        // Close the connection when an exception is raised.
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                cause);
        ctx.close();
    }
}
