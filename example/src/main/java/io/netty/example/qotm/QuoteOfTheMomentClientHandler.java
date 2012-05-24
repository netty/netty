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
package io.netty.example.qotm;

import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

public class QuoteOfTheMomentClientHandler extends ChannelInboundMessageHandlerAdapter<DatagramPacket> {


    @Override
    public void messageReceived(
            ChannelInboundHandlerContext<DatagramPacket> ctx, DatagramPacket msg)
            throws Exception {
        String response = msg.data().toString(CharsetUtil.UTF_8);
        if (response.startsWith("QOTM: ")) {
            System.out.println("Quote of the Moment: " + response.substring(6));
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(
            ChannelInboundHandlerContext<DatagramPacket> ctx, Throwable cause)
            throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
