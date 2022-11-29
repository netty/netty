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

import io.netty.security.core.Ports;
import io.netty.security.core.standards.StandardPorts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardPortsTest {

    @Test
    void from() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardPorts.from(1, 10);
            }
        });

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardPorts.from(101, 101);
            }
        });

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardPorts.from(5000, 5001);
            }
        });

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardPorts.from(0, 100);
            }
        });

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardPorts.from(101, 100);
            }
        });

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                StandardPorts.from(0, 65_536);
            }
        });
    }

    @Test
    void start() {
        assertEquals(5000, StandardPorts.from(5000, 10000).start());
        assertEquals(10000, StandardPorts.from(10000, 10000).start());
    }

    @Test
    void end() {
        assertEquals(10000, StandardPorts.from(5000, 10000).end());
        assertEquals(10002, StandardPorts.from(10000, 10002).end());
    }

    @Test
    void lookup() {
        Ports ports = StandardPorts.from(5, 30);
        assertTrue(ports.lookupPort(5));
        assertTrue(ports.lookupPort(22));
        assertTrue(ports.lookupPort(30));
    }

    @Test
    void compareTo() {
        Ports ports = StandardPorts.from(5, 30);
        assertEquals(-1, ports.lookup(1));
        assertEquals(-1, ports.lookup(4));
        assertEquals(0, ports.lookup(5));
        assertEquals(0, ports.lookup(22));
        assertEquals(0, ports.lookup(30));
        assertEquals(1, ports.lookup(31));
        assertEquals(1, ports.lookup(50));
    }
}
