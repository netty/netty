/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.qotm;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import io.netty.bootstrap.ConnectionlessBootstrap;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPipelineFactory;
import io.netty.channel.Channels;
import io.netty.channel.FixedReceiveBufferSizePredictorFactory;
import io.netty.channel.socket.DatagramChannelFactory;
import io.netty.channel.socket.nio.NioDatagramChannelFactory;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * A UDP server that responds to the QOTM (quote of the moment) request to a
 * {@link QuoteOfTheMomentClient}.
 *
 * Inspired by <a href="http://java.sun.com/docs/books/tutorial/networking/datagrams/clientServer.html">the official Java tutorial</a>.
 */
public class QuoteOfTheMomentServer {

    public static void main(String[] args) throws Exception {
        DatagramChannelFactory f =
            new NioDatagramChannelFactory(Executors.newCachedThreadPool());

        ConnectionlessBootstrap b = new ConnectionlessBootstrap(f);

        // Configure the pipeline factory.
        b.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(
                        new StringEncoder(CharsetUtil.ISO_8859_1),
                        new StringDecoder(CharsetUtil.ISO_8859_1),
                        new QuoteOfTheMomentServerHandler());
            }
        });

        // Server doesn't need to enable broadcast to listen to a broadcast.
        b.setOption("broadcast", "false");

        // Allow packets as large as up to 1024 bytes (default is 768).
        // You could increase or decrease this value to avoid truncated packets
        // or to improve memory footprint respectively.
        //
        // Please also note that a large UDP packet might be truncated or
        // dropped by your router no matter how you configured this option.
        // In UDP, a packet is truncated or dropped if it is larger than a
        // certain size, depending on router configuration.  IPv4 routers
        // truncate and IPv6 routers drop a large packet.  That's why it is
        // safe to send small packets in UDP.
        b.setOption(
                "receiveBufferSizePredictorFactory",
                new FixedReceiveBufferSizePredictorFactory(1024));

        // Bind to the port and start the service.
        b.bind(new InetSocketAddress(8080));
    }
}
