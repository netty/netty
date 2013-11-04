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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.testsuite.transport.socket.SocketTestPermutation.Factory;
import io.netty.testsuite.util.TestUtils;
import io.netty.util.NetUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;

public abstract class AbstractServerSocketTest {

    private static final List<Factory<ServerBootstrap>> COMBO = SocketTestPermutation.serverSocket();
    private static final List<ByteBufAllocator> ALLOCATORS = SocketTestPermutation.allocator();

    @Rule
    public final TestName testName = new TestName();

    protected final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());

    protected volatile ServerBootstrap sb;
    protected volatile InetSocketAddress addr;

    protected void run() throws Throwable {
        for (ByteBufAllocator allocator: ALLOCATORS) {
            int i = 0;
            for (Factory<ServerBootstrap> e: COMBO) {
                sb = e.newInstance();
                addr = new InetSocketAddress(
                        NetUtil.LOCALHOST, TestUtils.getFreePort());
                sb.localAddress(addr);
                sb.option(ChannelOption.ALLOCATOR, allocator);
                sb.childOption(ChannelOption.ALLOCATOR, allocator);

                logger.info(String.format(
                        "Running: %s %d of %d (%s) with %s",
                        testName.getMethodName(), ++ i, COMBO.size(), sb, StringUtil.simpleClassName(allocator)));
                try {
                    Method m = getClass().getDeclaredMethod(
                            testName.getMethodName(), ServerBootstrap.class);
                    m.invoke(this, sb);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }
        }
    }
}
