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
package io.netty.channel.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class TransportSocketTest {
    private static final Logger logger = LoggerFactory.getLogger(TransportSocketTest.class);

    public TransportSocketTest() {
    }

    private final Bootstrap b = new Bootstrap();

    @Test
    public void testTransportSocketThroughSocksProxyWithNoAuth() throws InterruptedException {
        b.group(new OioEventLoopGroup())
                .channel(OioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.PROXY, new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080)))
                .handler(new LoggingHandler());
        logger.debug("Connecting to ...");
        ChannelFuture future = b.connect(InetSocketAddress.createUnresolved("google.com", 80));
        future.addListener(new GenericFutureListener<Future<Void>>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                logger.debug("operationIsDone: " + future.isDone());
                logger.debug("operationComplete: " + future.isSuccess());
            }
        });

        future.sync();
        logger.debug(future.channel().remoteAddress().toString());
    }

    @Test
    public void testTransportSocketThroughSocksProxyWithPasswordAuth() {
    }
}
