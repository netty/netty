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
package io.netty.example.dns.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import java.util.concurrent.ConcurrentMap;

/**
 * Handles responses from remote DNS servers, looks up the Callback that
 * requested a response and invokes it..
 */
public class DnsClientInboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final ConcurrentMap<Integer, DnsClient.Callback> callbacks;
    private final DnsResponseDecoder drd = new DnsResponseDecoder();

    DnsClientInboundHandler(ConcurrentMap<Integer, DnsClient.Callback> callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
        DnsResponse resp = drd.decode(ctx, msg);
        int id = resp.header().id();
        DnsClient.Callback callback = callbacks.get(id);
        if (callback != null) {
            try {
                callback.onResponseReceived(resp);
            } catch (Exception e) {
                exceptionCaught(ctx, e);
            } finally {
                callbacks.remove(id);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
