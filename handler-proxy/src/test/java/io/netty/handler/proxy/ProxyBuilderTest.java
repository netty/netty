/*
 * Copyright 2015 The Netty Project
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

package io.netty.handler.proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;

import org.junit.Test;

import io.netty.handler.proxy.ProxyHandlerBuilder.ProxyType;

public class ProxyBuilderTest {

    @Test
    public void instanceHttp() {
        ProxyHandler proxyHandler = ProxyHandlerBuilder.forType(ProxyType.HTTP).proxyAddress("localhost", 8080).build();
        assertTrue(proxyHandler instanceof HttpProxyHandler);
        assertEquals("none", proxyHandler.authScheme());
        InetSocketAddress proxyAddress = proxyHandler.proxyAddress();
        assertEquals("localhost", proxyAddress.getHostName());
        assertEquals(8080, proxyAddress.getPort());
    }

    @Test
    public void instanceSocks4() {
        ProxyHandler proxyHandler = ProxyHandlerBuilder.forType(ProxyType.SOCKS4).proxyAddress("localhost", 8080)
                .build();
        assertTrue(proxyHandler instanceof Socks4ProxyHandler);
        assertEquals("none", proxyHandler.authScheme());
        InetSocketAddress proxyAddress = proxyHandler.proxyAddress();
        assertEquals("localhost", proxyAddress.getHostName());
        assertEquals(8080, proxyAddress.getPort());
    }

    @Test
    public void instanceSocks5() {
        ProxyHandler proxyHandler = ProxyHandlerBuilder.forType(ProxyType.SOCKS5).proxyAddress("localhost", 8080)
                .build();
        assertTrue(proxyHandler instanceof Socks5ProxyHandler);
        assertEquals("none", proxyHandler.authScheme());
        InetSocketAddress proxyAddress = proxyHandler.proxyAddress();
        assertEquals("localhost", proxyAddress.getHostName());
        assertEquals(8080, proxyAddress.getPort());
    }

    @Test
    public void instanceSocks5StringType() {
        ProxyHandler proxyHandler = ProxyHandlerBuilder.forType("socks5").proxyAddress("localhost", 8080).build();
        assertTrue(proxyHandler instanceof Socks5ProxyHandler);
    }

    @Test
    public void proxyAuthHttp() {
        ProxyHandler proxyHandler = ProxyHandlerBuilder.forType(ProxyType.HTTP).proxyAddress("localhost", 8080)
                .username("foo").password("bar").build();
        assertEquals("basic", proxyHandler.authScheme());
        HttpProxyHandler httpProxyhandler = (HttpProxyHandler) proxyHandler;
        assertEquals("foo", httpProxyhandler.username());
        assertEquals("bar", httpProxyhandler.password());
    }

    @Test
    public void proxyAuthSocks4() {
        ProxyHandler proxyHandler = ProxyHandlerBuilder.forType(ProxyType.SOCKS4).proxyAddress("localhost", 8080)
                .username("foo").build();
        assertEquals("username", proxyHandler.authScheme());
        Socks4ProxyHandler socksProxyhandler = (Socks4ProxyHandler) proxyHandler;
        assertEquals("foo", socksProxyhandler.username());
    }

    @Test
    public void proxyAuthSocks5() {
        ProxyHandler proxyHandler = ProxyHandlerBuilder.forType(ProxyType.SOCKS5).proxyAddress("localhost", 8080)
                .username("foo").password("bar").build();
        assertEquals("password", proxyHandler.authScheme());
        Socks5ProxyHandler socksProxyhandler = (Socks5ProxyHandler) proxyHandler;
        assertEquals("foo", socksProxyhandler.username());
        assertEquals("bar", socksProxyhandler.password());
    }

    @Test
    public void proxyAddress() {
        ProxyHandler proxyHandler = ProxyHandlerBuilder.forType(ProxyType.HTTP).proxyAddress("localhost", 8080).build();
        InetSocketAddress proxyAddress = proxyHandler.proxyAddress();
        assertEquals("localhost", proxyAddress.getHostName());
        assertEquals(8080, proxyAddress.getPort());

        InetSocketAddress testProxyAddress = InetSocketAddress.createUnresolved("127.0.0.1", 8888);
        proxyHandler = ProxyHandlerBuilder.forType(ProxyType.HTTP).proxyAddress(testProxyAddress).build();
        // a Socket address is used as is without conversion
        assertTrue(testProxyAddress == proxyHandler.proxyAddress());
    }

    @Test(expected = IllegalStateException.class)
    public void exceptionWithoutProxyAddress() {
        ProxyHandlerBuilder.forType(ProxyType.HTTP).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void exceptionForUnsupportedTypeString() {
        ProxyHandlerBuilder.forType("foo").build();
    }
}
