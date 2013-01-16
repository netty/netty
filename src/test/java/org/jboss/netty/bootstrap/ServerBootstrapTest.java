/*
 * Copyright 2013 The Netty Project
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
package org.jboss.netty.bootstrap;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ServerChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.Test;

import java.net.InetSocketAddress;

import static org.easymock.EasyMock.createMock;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ServerBootstrapTest extends BootstrapTest {
    @Override
    protected ServerBootstrap newBootstrap() {
        return new ServerBootstrap();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldOnlyAllowServerChannelFactory() {
        ServerBootstrap bootstrap = newBootstrap();
        bootstrap.setFactory(createMock(ChannelFactory.class));
    }

    @Test
    @Override
    public void shouldNotAllowFactoryToChangeMoreThanOnce() {
        Bootstrap b = newBootstrap();
        ChannelFactory f = createMock(ServerChannelFactory.class);
        b.setFactory(f);
        assertSame(f, b.getFactory());

        try {
            b.setFactory(createMock(ServerChannelFactory.class));
            fail();
        } catch (IllegalStateException e) {
            // Success.
        }
        b.releaseExternalResources();
    }

    @Test
    public void testBindAsync() {
        ServerBootstrap bootstrap = newBootstrap();
        bootstrap.setFactory(new NioServerSocketChannelFactory());
        ChannelFuture future = bootstrap.bindAsync(new InetSocketAddress(0));
        future.awaitUninterruptibly();
        assertTrue(future.isSuccess());
        future.getChannel().close().awaitUninterruptibly();
    }

    @Test
    public void testBind() {
        ServerBootstrap bootstrap = newBootstrap();
        bootstrap.setFactory(new NioServerSocketChannelFactory());
        Channel channel = bootstrap.bind(new InetSocketAddress(0));
        channel.close().awaitUninterruptibly();
    }
}
