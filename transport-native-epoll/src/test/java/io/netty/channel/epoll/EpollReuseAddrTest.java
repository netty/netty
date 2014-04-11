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
package io.netty.channel.epoll;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.testsuite.util.TestUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

public class EpollReuseAddrTest {
    private static final int MAJOR;
    private static final int MINOR;
    private static final int BUGFIX;
    static {
        String kernelVersion = Native.kernelVersion();
        int index = kernelVersion.indexOf("-");
        if (index > -1) {
            kernelVersion = kernelVersion.substring(0, index);
        }
        String[] versionParts = kernelVersion.split("\\.");
        if (versionParts.length == 3) {
            MAJOR = Integer.parseInt(versionParts[0]);
            MINOR = Integer.parseInt(versionParts[1]);
            BUGFIX = Integer.parseInt(versionParts[2]);
        } else {
            throw new IllegalStateException();
        }
    }

    @Test
    public void testMultipleBindWithoutReusePortFails() {
        Assume.assumeTrue(versionEqOrGt(3, 9, 0));
        ServerBootstrap bootstrap = createBootstrap();
        ChannelFuture future = bootstrap.bind().syncUninterruptibly();
        try {
            bootstrap.bind().syncUninterruptibly();
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IOException);
        }
        future.channel().close().syncUninterruptibly();
    }

    @Test
    public void testMultipleBind() {
        Assume.assumeTrue(versionEqOrGt(3, 9, 0));
        ServerBootstrap bootstrap = createBootstrap();
        bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
        ChannelFuture future = bootstrap.bind().syncUninterruptibly();
        ChannelFuture future2 = bootstrap.bind().syncUninterruptibly();

        future.channel().close().syncUninterruptibly();
        future2.channel().close().syncUninterruptibly();
    }

    private ServerBootstrap createBootstrap() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(EpollSocketTestPermutation.EPOLL_BOSS_GROUP, EpollSocketTestPermutation.EPOLL_WORKER_GROUP);
        bootstrap.channel(EpollServerSocketChannel.class);
        bootstrap.childHandler(new ChannelHandlerAdapter() { });
        bootstrap.option(EpollChannelOption.SO_REUSEADDR, true);
        InetSocketAddress address = new InetSocketAddress(TestUtils.getFreePort());
        bootstrap.localAddress(address);
        return bootstrap;
    }

    private static boolean versionEqOrGt(int major, int minor, int bugfix)  {
        return major > MAJOR || minor > MINOR || bugfix >= BUGFIX;
    }
}
