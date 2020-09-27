/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket;

import io.netty.util.NetUtil;
import org.junit.Test;

import java.net.InetAddress;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class InternetProtocolFamilyTest {
    @Test
    public void ipv4ShouldHaveLocalhostOfIpV4() {
        assertThat(InternetProtocolFamily.IPv4.localhost(), is((InetAddress) NetUtil.LOCALHOST4));
    }

    @Test
    public void ipv6ShouldHaveLocalhostOfIpV6() {
        assertThat(InternetProtocolFamily.IPv6.localhost(), is((InetAddress) NetUtil.LOCALHOST6));
    }
}
