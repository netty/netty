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
package org.jboss.netty.handler.ssl;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

public class SslCloseTest extends SslTest{

    public SslCloseTest(
            SslContext serverCtx, SslContext clientCtx,
            ChannelFactory serverChannelFactory, ChannelFactory clientChannelFactory) {
        super(serverCtx, clientCtx, serverChannelFactory, clientChannelFactory);
    }

    /**
     * Try to write a testcase to reproduce #343
     */
    @Test
    public void testCloseOnSslException() {
        ServerBootstrap sb = new ServerBootstrap(serverChannelFactory);
        ClientBootstrap cb = new ClientBootstrap(clientChannelFactory);

        sb.getPipeline().addFirst("ssl", serverCtx.newHandler());
        sb.getPipeline().addLast("handler", new SimpleChannelUpstreamHandler() {

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
                e.getCause().printStackTrace();
                ctx.getChannel().close();
            }

        });

        Channel serverChannel = sb.bind(new InetSocketAddress(0));

        Channel cc = cb.connect(serverChannel.getLocalAddress()).awaitUninterruptibly().getChannel();
        cc.write(ChannelBuffers.copiedBuffer("unencrypted", CharsetUtil.US_ASCII)).awaitUninterruptibly();

        Assert.assertTrue(cc.getCloseFuture().awaitUninterruptibly(5000));

        serverChannel.close();
    }
}
