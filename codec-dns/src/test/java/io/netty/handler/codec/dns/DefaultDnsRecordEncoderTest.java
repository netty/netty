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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import org.junit.Test;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;

public class DefaultDnsRecordEncoderTest {

    @Test
    public void testEncodeName() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io.");
    }

    @Test
    public void testEncodeNameWithoutTerminator() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io");
    }

    @Test
    public void testEncodeNameWithExtraTerminator() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io..");
    }

    // Test for https://github.com/netty/netty/issues/5014
    @Test
    public void testEncodeEmptyName() throws Exception {
        testEncodeName(new byte[] { 0 }, StringUtil.EMPTY_STRING);
    }

    @Test
    public void testEncodeRootName() throws Exception {
        testEncodeName(new byte[] { 0 }, ".");
    }

    private static void testEncodeName(byte[] expected, String name) throws Exception {
        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        ByteBuf out = Unpooled.buffer();
        ByteBuf expectedBuf = Unpooled.wrappedBuffer(expected);
        try {
            encoder.encodeName(name, out);
            assertEquals(expectedBuf, out);
        } finally {
            out.release();
            expectedBuf.release();
        }
    }

    @Test
    public void testOptEcsRecordIpv4() throws Exception {
        testOptEcsRecordIp(SocketUtils.addressByName("1.2.3.4"));
        testOptEcsRecordIp(SocketUtils.addressByName("1.2.3.255"));
    }

    @Test
    public void testOptEcsRecordIpv6() throws Exception {
        testOptEcsRecordIp(SocketUtils.addressByName("::0"));
        testOptEcsRecordIp(SocketUtils.addressByName("::FF"));
    }

    private static void testOptEcsRecordIp(InetAddress address) throws Exception {
        int addressBits = address.getAddress().length * Byte.SIZE;
        for (int i = 0; i <= addressBits; ++i) {
            testIp(address, i);
        }
    }

    private static void testIp(InetAddress address, int prefix) throws Exception {
        int lowOrderBitsToPreserve = prefix % Byte.SIZE;

        ByteBuf addressPart = Unpooled.wrappedBuffer(address.getAddress(), 0,
                DefaultDnsRecordEncoder.calculateEcsAddressLength(prefix, lowOrderBitsToPreserve));

        if (lowOrderBitsToPreserve > 0) {
            // Pad the leftover of the last byte with zeros.
            int idx = addressPart.writerIndex() - 1;
            byte lastByte = addressPart.getByte(idx);
            int paddingMask = ~((1 << (8 - lowOrderBitsToPreserve)) - 1);
            addressPart.setByte(idx, lastByte & paddingMask);
        }

        int payloadSize = nextInt(Short.MAX_VALUE);
        int extendedRcode = nextInt(Byte.MAX_VALUE * 2); // Unsigned
        int version = nextInt(Byte.MAX_VALUE * 2); // Unsigned

        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        ByteBuf out = Unpooled.buffer();
        try {
            DnsOptEcsRecord record = new DefaultDnsOptEcsRecord(
                    payloadSize, extendedRcode, version, prefix, address.getAddress());
            encoder.encodeRecord(record, out);

            assertEquals(0, out.readByte()); // Name
            assertEquals(DnsRecordType.OPT.intValue(), out.readUnsignedShort()); // Opt
            assertEquals(payloadSize, out.readUnsignedShort()); // payload
            assertEquals(record.timeToLive(), out.getUnsignedInt(out.readerIndex()));

            // Read unpacked TTL.
            assertEquals(extendedRcode, out.readUnsignedByte());
            assertEquals(version, out.readUnsignedByte());
            assertEquals(extendedRcode, record.extendedRcode());
            assertEquals(version, record.version());
            assertEquals(0, record.flags());

            assertEquals(0, out.readShort());

            int payloadLength = out.readUnsignedShort();
            assertEquals(payloadLength, out.readableBytes());

            assertEquals(8, out.readShort()); // As defined by RFC.

            int rdataLength = out.readUnsignedShort();
            assertEquals(rdataLength, out.readableBytes());

            assertEquals((short) InternetProtocolFamily.of(address).addressNumber(), out.readShort());

            assertEquals(prefix, out.readUnsignedByte());
            assertEquals(0, out.readUnsignedByte()); // This must be 0 for requests.
            assertEquals(addressPart, out);
        } finally {
            addressPart.release();
            out.release();
        }
    }

    private static int nextInt(int max) {
        return PlatformDependent.threadLocalRandom().nextInt(max);
    }
}
