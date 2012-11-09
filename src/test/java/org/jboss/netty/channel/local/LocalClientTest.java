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
package org.jboss.netty.channel.local;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.junit.Test;

public class LocalClientTest {

    @Test
    public void testLocalAddressBind() throws InterruptedException {
        ServerBootstrap sb = new ServerBootstrap(new DefaultLocalServerChannelFactory());

        LocalAddress addr = new LocalAddress("Server");
        LocalAddress addr2 = new LocalAddress("C1");
        sb.bind(addr);


        ClientBootstrap cb = new ClientBootstrap(new DefaultLocalClientChannelFactory());
        final ExceptionHandler handler = new ExceptionHandler();
        cb.setPipelineFactory(new ChannelPipelineFactory() {

            public ChannelPipeline getPipeline() throws Exception {
                return Channels.pipeline(handler);
            }
        });
        ChannelFuture cf = cb.connect(addr, addr2).awaitUninterruptibly();

        assertTrue(cf.isSuccess());
        assertTrue(cf.getChannel().close().awaitUninterruptibly().isSuccess());

        assertNull(handler.await());
        sb.releaseExternalResources();
        cb.releaseExternalResources();
    }

    static final class ExceptionHandler extends SimpleChannelUpstreamHandler {
        private final CountDownLatch latch = new CountDownLatch(1);
        private Throwable t;

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            super.exceptionCaught(ctx, e);
            t = e.getCause();
        }

        @Override
        public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            super.channelClosed(ctx, e);
            latch.countDown();
        }

        public Throwable await() throws InterruptedException {
            latch.await();
            return t;

        }

    }
}
