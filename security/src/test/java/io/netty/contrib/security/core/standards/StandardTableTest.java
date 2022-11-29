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
package io.netty.contrib.security.core.standards;

import io.netty.security.core.Action;
import io.netty.security.core.IpAddress;
import io.netty.security.core.IpAddresses;
import io.netty.security.core.Protocol;
import io.netty.security.core.Rule;
import io.netty.security.core.StaticIpAddress;
import io.netty.security.core.Table;
import io.netty.security.core.standards.StandardFiveTuple;
import io.netty.security.core.standards.StandardPorts;
import io.netty.security.core.standards.StandardRule;
import io.netty.security.core.standards.StandardTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardTableTest {

    @Test
    void of() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardTable.of(1, "CatTable");
            }
        });

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardTable.of(0, "MeowTable");
            }
        });

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardTable.of(Integer.MAX_VALUE, "MeowTable");
            }
        });

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardTable.of(Integer.MAX_VALUE - 1, "MeowTable");
            }
        });
    }

    @Test
    void priority() {
        Table table = StandardTable.of(9110, "Meow");
        assertEquals(9110, table.priority());
    }

    @Test
    void name() {
        Table table = StandardTable.of(9110, "NettySecurity");
        assertEquals("NettySecurity", table.name());
    }

    @Test
    void rules() {
        StandardTable table = StandardTable.of(9110, "NettySecurity");

        Rule rule = StandardRule.newBuilder()
                .withProtocol(Protocol.UDP)
                .withSourcePorts(StandardPorts.from(50000, 55555))
                .withDestinationPorts(StandardPorts.of(80))
                .withSourceIpAddresses(IpAddresses.create(IpAddress.of("192.168.1.0", 24)))
                .withDestinationIpAddress(IpAddresses.create(IpAddress.of("10.10.10.10", 32)))
                .withAction(Action.ACCEPT)
                .build();

        table.unlock();
        assertFalse(table.isLocked());

        table.add(rule);
        table.lock();
        assertTrue(table.isLocked());

        assertEquals(rule, table.rules().get(0));
    }

    @Test
    void lookup() throws UnknownHostException {
        StandardTable table = StandardTable.of(9110, "NettySecurity");
        table.unlock();
        assertFalse(table.isLocked());

        IpAddress srcAddress = IpAddress.of("192.168.1.0", 24);
        IpAddress dstAddress = IpAddress.of("10.10.10.10", 32);

        for (int i = 1; i <= 65_000; i++) {
            Rule rule = StandardRule.newBuilder()
                    .withProtocol(Protocol.UDP)
                    .withSourcePorts(StandardPorts.from(50000, 55555))
                    .withDestinationPorts(StandardPorts.of(i))
                    .withSourceIpAddresses(IpAddresses.create(srcAddress))
                    .withDestinationIpAddress(IpAddresses.create(dstAddress))
                    .withAction(Action.ACCEPT)
                    .build();

            table.add(rule);
        }

        table.lock();
        assertTrue(table.isLocked());

        Rule rule = table.lookup(StandardFiveTuple.from(Protocol.UDP, 55555, 60000,
                StaticIpAddress.of("192.168.1.10"), StaticIpAddress.of("10.10.10.10")));

        assertNotNull(rule);

        assertEquals(Protocol.UDP, rule.protocol());
        assertEquals(50000, rule.sourcePorts().start());
        assertEquals(55555, rule.sourcePorts().end());
        assertEquals(60000, rule.destinationPorts().start());
        assertEquals(srcAddress.address(), rule.sourceIpAddresses().copy().get(0).address());
        assertEquals(dstAddress.address(), rule.destinationIpAddresses().copy().get(0).address());
        assertEquals(Action.ACCEPT, rule.action());
    }
}
