/*
 * Copyright 2026 The Netty Project
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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractDnsOptPseudoRrRecordTest {

    // DNSSEC OK, see https://tools.ietf.org/html/rfc3225#section-3
    private static final int DO = 0x8000;

    // Compact Answers OK, see https://www.rfc-editor.org/rfc/rfc9824.html#section-5.1
    private static final int CO = 0x4000;

    private static DnsOptPseudoRecord newRecord(int maxPayloadSize, int extendedRcode, int version, int flags) {
        return new AbstractDnsOptPseudoRrRecord(maxPayloadSize, extendedRcode, version, flags) { };
    }

    @Test
    public void testFlagsCanCarryDo() {
        DnsOptPseudoRecord record = newRecord(4096, 0, 0, DO);
        assertEquals(DO, record.flags());
        assertEquals(0, record.extendedRcode());
        assertEquals(0, record.version());
    }

    @Test
    public void testFlagsIsNotTruncatedToEightBits() {
        // The flags field is 16 bits wide, so bits 8..15 must survive as well.
        DnsOptPseudoRecord record = newRecord(4096, 0, 0, 0x7fff);
        assertEquals(0x7fff, record.flags());
    }

    @Test
    public void testFlagsCanCarryBitsOtherThanDo() {
        // Bits below DO are assigned by the IANA EDNS Header Flags registry, so they must not be masked off.
        assertEquals(CO, newRecord(4096, 0, 0, CO).flags());
        assertEquals(DO | CO, newRecord(4096, 0, 0, DO | CO).flags());
    }

    @Test
    public void testTtlLayoutIsExtendedRcodeVersionFlags() {
        // Asymmetric values so that swapping or widening any of the three fields is detectable.
        DnsOptPseudoRecord record = newRecord(4096, 0x12, 0x34, 0x8abc);
        assertEquals(0x12348abcL, record.timeToLive());
        assertEquals(0x12, record.extendedRcode());
        assertEquals(0x34, record.version());
        assertEquals(0x8abc, record.flags());
    }

    @Test
    public void testAllFieldsAtMaximum() {
        DnsOptPseudoRecord record = newRecord(1232, 0xff, 0xff, 0xffff);
        assertEquals(0xff, record.extendedRcode());
        assertEquals(0xff, record.version());
        assertEquals(0xffff, record.flags());
        assertEquals(0xffffffffL, record.timeToLive());
        assertEquals(1232, record.dnsClass());
    }

    @Test
    public void testFlagsDoNotLeakIntoExtendedRcodeOrVersion() {
        DnsOptPseudoRecord record = newRecord(4096, 0, 0, 0xffff);
        assertEquals(0, record.extendedRcode());
        assertEquals(0, record.version());
    }

    @Test
    public void testExtendedRcodeAndVersionDoNotLeakIntoFlags() {
        DnsOptPseudoRecord record = newRecord(4096, 0xff, 0xff, 0);
        assertEquals(0, record.flags());
    }

    @Test
    public void testOutOfRangeValuesAreTruncatedToTheirFieldWidth() {
        // 0x01 for the extended RCODE so a bit bleeding out of an over-wide VERSION is visible.
        DnsOptPseudoRecord record = newRecord(4096, 0x01, 0x2ff, 0x3ffff);
        assertEquals(0x01, record.extendedRcode());
        assertEquals(0xff, record.version());
        assertEquals(0xffff, record.flags());
        assertEquals(0x01ffffffL, record.timeToLive());

        // A negative value must never produce a negative TTL, which AbstractDnsRecord rejects.
        DnsOptPseudoRecord negative = newRecord(4096, -1, -1, -1);
        assertEquals(0xffffffffL, negative.timeToLive());
        assertEquals(0xffff, negative.flags());
    }

    @Test
    public void testPreExistingConstructorsKeepTtlUnchanged() {
        // The constructors that predate the flags parameter must produce exactly the TTL they did before.
        DnsOptPseudoRecord threeArg = new AbstractDnsOptPseudoRrRecord(4096, 0xab, 0xcd) { };
        assertEquals(0xabcd0000L, threeArg.timeToLive());
        assertEquals(0, threeArg.flags());

        DnsOptPseudoRecord oneArg = new AbstractDnsOptPseudoRrRecord(4096) { };
        assertEquals(0L, oneArg.timeToLive());
        assertEquals(0, oneArg.flags());
    }

    @Test
    public void testDoIsEncodedOnTheWire() throws Exception {
        assertEncoded(newRecord(4096, 0, 0, DO), 4096, 0, 0, DO);
    }

    @Test
    public void testExtendedRcodeVersionAndFlagsAreEncodedOnTheWire() throws Exception {
        assertEncoded(newRecord(1232, 0x01, 0x02, DO | CO), 1232, 0x01, 0x02, DO | CO);
    }

    private static void assertEncoded(DnsOptPseudoRecord record, int maxPayloadSize,
                                      int extendedRcode, int version, int flags) throws Exception {
        ByteBuf out = Unpooled.buffer();
        try {
            new DefaultDnsRecordEncoder().encodeRecord(record, out);

            // See https://tools.ietf.org/html/rfc6891#section-6.1.2
            assertEquals(0, out.readByte()); // NAME must be the root domain.
            assertEquals(DnsRecordType.OPT.intValue(), out.readUnsignedShort()); // TYPE
            assertEquals(maxPayloadSize, out.readUnsignedShort()); // CLASS holds the UDP payload size.
            assertEquals(extendedRcode, out.readUnsignedByte()); // TTL: EXTENDED-RCODE
            assertEquals(version, out.readUnsignedByte()); // TTL: VERSION
            assertEquals(flags, out.readUnsignedShort()); // TTL: DO + Z
            assertEquals(0, out.readUnsignedShort()); // RDLENGTH
            assertEquals(0, out.readableBytes());
        } finally {
            out.release();
        }
    }
}
