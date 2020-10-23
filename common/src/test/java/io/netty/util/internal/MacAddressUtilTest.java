/*
 * Copyright 2016 The Netty Project
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
package io.netty.util.internal;

import org.junit.Test;

import static io.netty.util.internal.EmptyArrays.EMPTY_BYTES;
import static io.netty.util.internal.MacAddressUtil.parseMAC;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MacAddressUtilTest {
    @Test
    public void testCompareAddresses() {
        // should not prefer empty address when candidate is not globally unique
        assertEquals(
                0,
                MacAddressUtil.compareAddresses(
                        EMPTY_BYTES,
                        new byte[]{(byte) 0x52, (byte) 0x54, (byte) 0x00, (byte) 0xf9, (byte) 0x32, (byte) 0xbd}));

        // only candidate is globally unique
        assertEquals(
                -1,
                MacAddressUtil.compareAddresses(
                        EMPTY_BYTES,
                        new byte[]{(byte) 0x50, (byte) 0x54, (byte) 0x00, (byte) 0xf9, (byte) 0x32, (byte) 0xbd}));

        // only candidate is globally unique
        assertEquals(
                -1,
                MacAddressUtil.compareAddresses(
                        new byte[]{(byte) 0x52, (byte) 0x54, (byte) 0x00, (byte) 0xf9, (byte) 0x32, (byte) 0xbd},
                        new byte[]{(byte) 0x50, (byte) 0x54, (byte) 0x00, (byte) 0xf9, (byte) 0x32, (byte) 0xbd}));

        // only current is globally unique
        assertEquals(
                1,
                MacAddressUtil.compareAddresses(
                        new byte[]{(byte) 0x52, (byte) 0x54, (byte) 0x00, (byte) 0xf9, (byte) 0x32, (byte) 0xbd},
                        EMPTY_BYTES));

        // only current is globally unique
        assertEquals(
                1,
                MacAddressUtil.compareAddresses(
                        new byte[]{(byte) 0x50, (byte) 0x54, (byte) 0x00, (byte) 0xf9, (byte) 0x32, (byte) 0xbd},
                        new byte[]{(byte) 0x52, (byte) 0x54, (byte) 0x00, (byte) 0xf9, (byte) 0x32, (byte) 0xbd}));

        // both are globally unique
        assertEquals(
                0,
                MacAddressUtil.compareAddresses(
                        new byte[]{(byte) 0x50, (byte) 0x54, (byte) 0x00, (byte) 0xf9, (byte) 0x32, (byte) 0xbd},
                        new byte[]{(byte) 0x50, (byte) 0x55, (byte) 0x01, (byte) 0xfa, (byte) 0x33, (byte) 0xbe}));
    }

    @Test
    public void testParseMacEUI48() {
        assertArrayEquals(new byte[]{0, (byte) 0xaa, 0x11, (byte) 0xbb, 0x22, (byte) 0xcc},
                parseMAC("00-AA-11-BB-22-CC"));
        assertArrayEquals(new byte[]{0, (byte) 0xaa, 0x11, (byte) 0xbb, 0x22, (byte) 0xcc},
                parseMAC("00:AA:11:BB:22:CC"));
    }

    @Test
    public void testParseMacMAC48ToEUI64() {
        // MAC-48 into an EUI-64
        assertArrayEquals(new byte[]{0, (byte) 0xaa, 0x11, (byte) 0xff, (byte) 0xff, (byte) 0xbb, 0x22, (byte) 0xcc},
                parseMAC("00-AA-11-FF-FF-BB-22-CC"));
        assertArrayEquals(new byte[]{0, (byte) 0xaa, 0x11, (byte) 0xff, (byte) 0xff, (byte) 0xbb, 0x22, (byte) 0xcc},
                parseMAC("00:AA:11:FF:FF:BB:22:CC"));
    }

    @Test
    public void testParseMacEUI48ToEUI64() {
        // EUI-48 into an EUI-64
        assertArrayEquals(new byte[]{0, (byte) 0xaa, 0x11, (byte) 0xff, (byte) 0xfe, (byte) 0xbb, 0x22, (byte) 0xcc},
                parseMAC("00-AA-11-FF-FE-BB-22-CC"));
        assertArrayEquals(new byte[]{0, (byte) 0xaa, 0x11, (byte) 0xff, (byte) 0xfe, (byte) 0xbb, 0x22, (byte) 0xcc},
                parseMAC("00:AA:11:FF:FE:BB:22:CC"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalid7HexGroupsA() {
        parseMAC("00-AA-11-BB-22-CC-FF");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalid7HexGroupsB() {
        parseMAC("00:AA:11:BB:22:CC:FF");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalidEUI48MixedSeparatorA() {
        parseMAC("00-AA:11-BB-22-CC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalidEUI48MixedSeparatorB() {
        parseMAC("00:AA-11:BB:22:CC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalidEUI64MixedSeparatorA() {
        parseMAC("00-AA-11-FF-FE-BB-22:CC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalidEUI64MixedSeparatorB() {
        parseMAC("00:AA:11:FF:FE:BB:22-CC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalidEUI48TrailingSeparatorA() {
        parseMAC("00-AA-11-BB-22-CC-");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalidEUI48TrailingSeparatorB() {
        parseMAC("00:AA:11:BB:22:CC:");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalidEUI64TrailingSeparatorA() {
        parseMAC("00-AA-11-FF-FE-BB-22-CC-");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseMacInvalidEUI64TrailingSeparatorB() {
        parseMAC("00:AA:11:FF:FE:BB:22:CC:");
    }
}
