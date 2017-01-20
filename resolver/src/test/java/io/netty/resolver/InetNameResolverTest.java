/*@
 * Copyright 2017 The Netty Project
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
package io.netty.resolver;

import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class InetNameResolverTest {

    @Test
    public void testResolveEmpty() throws UnknownHostException {
        testResolve0(SocketUtils.addressByName(StringUtil.EMPTY_STRING), StringUtil.EMPTY_STRING);
    }

    @Test
    public void testResolveNull() throws UnknownHostException {
        testResolve0(SocketUtils.addressByName(null), null);
    }

    @Test
    public void testResolveAllEmpty() throws UnknownHostException {
        testResolveAll0(Arrays.asList(
                SocketUtils.allAddressesByName(StringUtil.EMPTY_STRING)), StringUtil.EMPTY_STRING);
    }

    @Test
    public void testResolveAllNull() throws UnknownHostException {
        testResolveAll0(Arrays.asList(
                SocketUtils.allAddressesByName(null)), null);
    }

    private static void testResolve0(InetAddress expectedAddr, String name) {
        InetNameResolver resolver = new TestInetNameResolver();
        try {
            InetAddress address = resolver.resolve(name).syncUninterruptibly().getNow();
            assertEquals(expectedAddr, address);
        } finally {
            resolver.close();
        }
    }

    private static void testResolveAll0(List<InetAddress> expectedAddrs, String name) {
        InetNameResolver resolver = new TestInetNameResolver();
        try {
            List<InetAddress> addresses = resolver.resolveAll(name).syncUninterruptibly().getNow();
            assertEquals(expectedAddrs, addresses);
        } finally {
            resolver.close();
        }
    }

    private static final class TestInetNameResolver extends InetNameResolver {
        public TestInetNameResolver() {
            super(ImmediateEventExecutor.INSTANCE);
        }

        @Override
        protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
            promise.setFailure(new UnsupportedOperationException());
        }

        @Override
        protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {
            promise.setFailure(new UnsupportedOperationException());
        }
    }
}
