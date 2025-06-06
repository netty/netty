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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InternetProtocolFamilyTest {
    @Test
    public void ipv4ShouldHaveLocalhostOfIpV4() {
        assertEquals(NetUtil.LOCALHOST4, InternetProtocolFamily.IPv4.localhost());
    }

    @Test
    public void ipv6ShouldHaveLocalhostOfIpV6() {
        assertEquals(NetUtil.LOCALHOST6, InternetProtocolFamily.IPv6.localhost());
    }
}
