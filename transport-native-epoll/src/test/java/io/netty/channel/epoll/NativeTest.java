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

import io.netty.util.NetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.NativeInetAddress.address;

public class NativeTest {

    @Test
    public void testAddressIpv4() throws Exception {
        InetSocketAddress inetAddress = new InetSocketAddress(NetUtil.LOCALHOST4, 9999);
        byte[] bytes = new byte[8];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.put(inetAddress.getAddress().getAddress());
        buffer.putInt(inetAddress.getPort());
        Assert.assertEquals(inetAddress, address(buffer.array(), 0, bytes.length));
    }

    @Test
    public void testAddressIpv6() throws Exception {
        Inet6Address address = NetUtil.LOCALHOST6;
        InetSocketAddress inetAddress = new InetSocketAddress(address, 9999);
        byte[] bytes = new byte[24];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.put(address.getAddress());
        buffer.putInt(address.getScopeId());
        buffer.putInt(inetAddress.getPort());
        Assert.assertEquals(inetAddress, address(buffer.array(), 0, bytes.length));
    }
}
