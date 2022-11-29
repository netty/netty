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

import io.netty.security.core.FiveTuple;
import io.netty.security.core.Protocol;
import io.netty.security.core.StaticIpAddress;
import io.netty.security.core.standards.StandardFiveTuple;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardFiveTupleTest {

    @Test
    void from() throws Exception {
        FiveTuple fiveTuple = StandardFiveTuple.from(Protocol.UDP, 9110, 2580,
                StaticIpAddress.of("192.168.1.2"), StaticIpAddress.of("10.10.10.10"));

        assertEquals(Protocol.UDP, fiveTuple.protocol());
        assertEquals(9110, fiveTuple.sourcePort());
        assertEquals(2580, fiveTuple.destinationPort());
        assertEquals(StaticIpAddress.of("192.168.1.2"), fiveTuple.sourceIpAddress());
        assertEquals(StaticIpAddress.of("10.10.10.10"), fiveTuple.destinationIpAddress());
    }

    @Test
    void compareTo() throws Exception {
        FiveTuple fiveTuple = StandardFiveTuple.from(Protocol.UDP, 9110, 2580,
                StaticIpAddress.of("192.168.1.2"), StaticIpAddress.of("10.10.10.10"));


        FiveTuple standardFiveTuple = StandardFiveTuple.from(Protocol.UDP, 9110, 2580,
                StaticIpAddress.of("192.168.1.2"), StaticIpAddress.of("10.10.10.10"));
        assertEquals(0, fiveTuple.compareTo(standardFiveTuple));

        standardFiveTuple = StandardFiveTuple.from(Protocol.TCP, 9110, 2580,
                StaticIpAddress.of("192.168.1.2"), StaticIpAddress.of("10.10.10.10"));
        assertEquals(1, fiveTuple.compareTo(standardFiveTuple));
    }
}
