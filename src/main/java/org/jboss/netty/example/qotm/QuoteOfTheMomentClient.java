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
package org.jboss.netty.example.qotm;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;

/**
 * A UDP broadcast client that asks for a quote of the moment (QOTM) to
 * {@link QuoteOfTheMomentServer}.
 *
 * Inspired by <a href="http://java.sun.com/docs/books/tutorial/networking/datagrams/clientServer.html">the official Java tutorial</a>.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class QuoteOfTheMomentClient {

    public static void main(String[] args) throws Exception {
        DatagramChannelFactory f =
            new OioDatagramChannelFactory(Executors.newCachedThreadPool());

        ConnectionlessBootstrap b = new ConnectionlessBootstrap(f);

        // Configure the pipeline.
        ChannelPipeline p = b.getPipeline();
        p.addLast("encoder", new StringEncoder("UTF-8"));
        p.addLast("decoder", new StringDecoder("UTF-8"));
        p.addLast("handler", new QuoteOfTheMomentClientHandler());

        // Enable broadcast
        b.setOption("broadcast", "true");

        // Allow packets as large as up to 1024 bytes (default is 768).
        // You could increase or decrease this value to avoid truncated packets
        // or to improve memory footprint respectively.
        b.setOption(
                "receiveBufferSizePredictorFactory",
                new FixedReceiveBufferSizePredictorFactory(1024));

        DatagramChannel c = (DatagramChannel) b.bind(new InetSocketAddress(0));

        // Broadcast the QOTM request to port 8080.
        c.write("QOTM?", new InetSocketAddress("255.255.255.255", 8080));

        // QuoteOfTheMomentClientHandler will close the DatagramChannel when a
        // response is received.  If the channel is not closed within 5 seconds,
        // print an error message and quit.
        if (!c.getCloseFuture().awaitUninterruptibly(5000)) {
            System.err.println("QOTM request timed out.");
            c.close().awaitUninterruptibly();
        }

        f.releaseExternalResources();
    }
}
