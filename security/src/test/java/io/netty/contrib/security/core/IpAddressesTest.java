/*
 * Copyright 2022 The Netty Project
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
package io.netty.contrib.security.core;

import io.netty.security.core.Address;
import io.netty.security.core.IpAddress;
import io.netty.security.core.IpAddresses;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpAddressesTest {

    @Test
    void createEmpty() {
        IpAddresses ipAddresses = IpAddresses.create();
        assertTrue(ipAddresses.isLocked());
        ipAddresses.unlock();
        assertFalse(ipAddresses.isLocked());

        ipAddresses.add(IpAddress.of("192.168.1.25"));
        ipAddresses.add(IpAddress.of("10.1.2.3"));

        ipAddresses.lock();
        assertTrue(ipAddresses.isLocked());
    }

    @Test
    void createList() {
        List<Address> ipAddressList = new ArrayList<>();
        ipAddressList.add(IpAddress.of("192.168.1.25"));
        ipAddressList.add(IpAddress.of("10.1.2.3"));

        IpAddresses ipAddresses = IpAddresses.create(ipAddressList);
        assertTrue(ipAddresses.isLocked());
    }

    @Test
    void createVarArgs() {
        IpAddresses ipAddresses = IpAddresses.create(IpAddress.of("192.168.1.25"), IpAddress.of("10.1.2.3"));
        assertTrue(ipAddresses.isLocked());
    }
}
