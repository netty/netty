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
package io.netty.testsuite_native;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.kqueue.KQueue;
import io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

public class NativeLoadingTest {

    @Test
    @EnabledOnOs(OS.MAC)
    public void testNativeLoadingKqueue() {
        KQueue.ensureAvailability();
    }

    @Test
    @EnabledOnOs(OS.MAC)
    public void testNativeLoadingDnsServerAddressStreamProvider() {
        MacOSDnsServerAddressStreamProvider.ensureAvailability();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testNativeLoadingEpoll() {
        Epoll.ensureAvailability();
    }
}
