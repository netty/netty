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
package io.netty.resolver.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsOptCookieRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DnsQueryProtectionTest {

    private static final String NAME = "www.some-long-name.example.com.";

    private final InetSocketAddress nameServerAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 53);
    private final InetSocketAddress localAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345);
    private EmbeddedChannel channel;
    private DnsQueryContextManager manager;

    @BeforeEach
    public void setUp() {
        channel = new EmbeddedChannel();
        manager = new DnsQueryContextManager();
    }

    @AfterEach
    public void tearDown() {
        channel.finishAndReleaseAll();
    }

    private static final class Query {
        final DatagramDnsQueryContext ctx;
        final Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise;
        final int id;
        final String wireName;
        final byte[] cookie;

        Query(DatagramDnsQueryContext ctx, Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise,
              DatagramDnsQuery query) {
            this.ctx = ctx;
            this.promise = promise;
            this.id = query.id();
            this.wireName = ((DnsQuestion) query.recordAt(DnsSection.QUESTION)).name();
            byte[] cookie = null;
            for (int i = 0; i < query.count(DnsSection.ADDITIONAL); i++) {
                DnsRecord record = query.recordAt(DnsSection.ADDITIONAL, i);
                if (record instanceof DnsOptCookieRecord) {
                    cookie = ((DnsOptCookieRecord) record).clientCookie();
                }
            }
            this.cookie = cookie;
        }
    }

    private Query query(DnsQueryProtection protection) {
        Promise<AddressedEnvelope<DnsResponse, InetSocketAddress>> promise = channel.eventLoop().newPromise();
        DatagramDnsQueryContext ctx = new DatagramDnsQueryContext(channel, nameServerAddr, manager,
                4096, true, 5000,
                new DefaultDnsQuestion(NAME, DnsRecordType.A), new DnsRecord[0], promise, null, false, protection);
        ctx.writeQuery(true);
        DatagramDnsQuery query = channel.readOutbound();
        assertNotNull(query);
        try {
            return new Query(ctx, promise, query);
        } finally {
            query.release();
        }
    }

    private DatagramDnsResponse response(Query query, String questionName, ByteBuf optRdata) {
        DatagramDnsResponse response = new DatagramDnsResponse(nameServerAddr, localAddr, query.id);
        response.addRecord(DnsSection.QUESTION, new DefaultDnsQuestion(questionName, DnsRecordType.A));
        if (optRdata != null) {
            response.addRecord(DnsSection.ADDITIONAL,
                    new DefaultDnsRawRecord("", DnsRecordType.OPT, 4096, 0, optRdata));
        }
        return response;
    }

    private static ByteBuf cookieOption(byte[] cookie, int serverCookieLength) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(DnsOptCookieRecord.OPTION_CODE);
        buf.writeShort(cookie.length + serverCookieLength);
        buf.writeBytes(cookie);
        buf.writeZero(serverCookieLength);
        return buf;
    }

    private static String swapCase(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private static void assertAccepted(Query query) {
        assertTrue(query.promise.isSuccess());
        ReferenceCountUtil.release(query.promise.getNow());
    }

    private static void assertRejected(Query query) {
        assertFalse(query.promise.isDone());
    }

    @Test
    public void caseRandomizationSendsRandomizedNameAndAcceptsExactEcho() {
        Query query = query(new DnsQueryProtection(true, false));
        assertTrue(NAME.equalsIgnoreCase(query.wireName));

        query.ctx.finishSuccess(response(query, query.wireName, null), false);
        assertAccepted(query);
    }

    @Test
    public void caseRandomizationRejectsDifferentCase() {
        Query query = query(new DnsQueryProtection(true, false));

        // Same name but with every letter flipped, so it can never match what was sent.
        query.ctx.finishSuccess(response(query, swapCase(query.wireName), null), false);
        assertRejected(query);
        // The lower-case name of the original question must be rejected as well (unless the randomization picked
        // lower-case for every letter, which is statistically impossible for this name).
        assertNotEquals(NAME, query.wireName);
        query.ctx.finishSuccess(response(query, NAME, null), false);
        assertRejected(query);

        // The real response still gets through afterwards.
        query.ctx.finishSuccess(response(query, query.wireName, null), false);
        assertAccepted(query);
    }

    @Test
    public void caseRandomizationRejectsDifferentName() {
        Query query = query(new DnsQueryProtection(true, false));
        query.ctx.finishSuccess(response(query, "other.example.com.", null), false);
        assertRejected(query);
    }

    @Test
    public void caseIsIgnoredWithoutRandomization() {
        Query query = query(null);
        assertEquals(NAME, query.wireName);
        query.ctx.finishSuccess(response(query, NAME.toUpperCase(Locale.ROOT), null), false);
        assertAccepted(query);
    }

    @Test
    public void randomizeCaseKeepsPunycodeLabelsAndNonLetters() {
        DnsQueryProtection protection = new DnsQueryProtection(true, false);
        boolean changed = false;
        for (int i = 0; i < 100; i++) {
            String name = protection.randomizeCase(
                    new DefaultDnsQuestion("xn--80ak6aa92e.Exam-ple1.com.", DnsRecordType.A)).name();
            assertTrue(name.startsWith("xn--80ak6aa92e."), name);
            assertTrue("Exam-ple1.com.".equalsIgnoreCase(name.substring("xn--80ak6aa92e.".length())), name);
            changed |= !name.equals("xn--80ak6aa92e.Exam-ple1.com.");
        }
        assertTrue(changed);
    }

    @Test
    public void cookieIsSentAndEchoIsAccepted() {
        Query query = query(new DnsQueryProtection(false, true));
        assertNotNull(query.cookie);
        assertEquals(DnsOptCookieRecord.CLIENT_COOKIE_LENGTH, query.cookie.length);

        query.ctx.finishSuccess(response(query, NAME, cookieOption(query.cookie, 0)), false);
        assertAccepted(query);
    }

    @Test
    public void cookieIsDifferentForEveryQuery() {
        DnsQueryProtection protection = new DnsQueryProtection(false, true);
        Query query1 = query(protection);
        Query query2 = query(protection);
        assertFalse(Arrays.equals(query1.cookie, query2.cookie));
    }

    @Test
    public void cookieWithServerCookieIsAccepted() {
        Query query = query(new DnsQueryProtection(false, true));
        query.ctx.finishSuccess(response(query, NAME, cookieOption(query.cookie, 16)), false);
        assertAccepted(query);
    }

    @Test
    public void cookieMismatchIsRejected() {
        Query query = query(new DnsQueryProtection(false, true));
        byte[] other = query.cookie.clone();
        other[0] ^= 1;
        query.ctx.finishSuccess(response(query, NAME, cookieOption(other, 8)), false);
        assertRejected(query);
        query.ctx.finishSuccess(response(query, NAME, cookieOption(other, 0)), false);
        assertRejected(query);

        // Malformed options.
        ByteBuf truncated = cookieOption(query.cookie, 0);
        truncated.writerIndex(truncated.writerIndex() - 1);
        query.ctx.finishSuccess(response(query, NAME, truncated), false);
        assertRejected(query);
        // A server cookie that is too short.
        query.ctx.finishSuccess(response(query, NAME, cookieOption(query.cookie, 4)), false);
        assertRejected(query);
        // A cookie that is just too short.
        query.ctx.finishSuccess(response(query, NAME, cookieOption(new byte[4], 0)), false);
        assertRejected(query);

        query.ctx.finishSuccess(response(query, NAME, cookieOption(query.cookie, 8)), false);
        assertAccepted(query);
    }

    @Test
    public void missingCookieIsAcceptedUntilNameserverSupportedCookies() {
        DnsQueryProtection protection = new DnsQueryProtection(false, true);

        // The nameserver is not known to support cookies, so a response without is fine.
        Query query1 = query(protection);
        query1.ctx.finishSuccess(response(query1, NAME, null), false);
        assertAccepted(query1);

        Query query2 = query(protection);
        query2.ctx.finishSuccess(response(query2, NAME, cookieOption(query2.cookie, 8)), false);
        assertAccepted(query2);

        // Now it did, so omitting the cookie must be rejected.
        Query query3 = query(protection);
        query3.ctx.finishSuccess(response(query3, NAME, null), false);
        assertRejected(query3);
        // Also when there is an OPT record but without the cookie.
        query3.ctx.finishSuccess(response(query3, NAME, Unpooled.EMPTY_BUFFER), false);
        assertRejected(query3);

        query3.ctx.finishSuccess(response(query3, NAME, cookieOption(query3.cookie, 8)), false);
        assertAccepted(query3);
    }

    @Test
    public void bothDefencesCanBeCombined() {
        Query query = query(new DnsQueryProtection(true, true));
        assertNotNull(query.cookie);

        query.ctx.finishSuccess(response(query, swapCase(query.wireName), cookieOption(query.cookie, 8)), false);
        assertRejected(query);
        query.ctx.finishSuccess(response(query, query.wireName, cookieOption(new byte[8], 8)), false);
        assertRejected(query);
        query.ctx.finishSuccess(response(query, query.wireName, cookieOption(query.cookie, 8)), false);
        assertAccepted(query);
    }

    @Test
    public void noCookieWhenDisabled() {
        Query query = query(new DnsQueryProtection(true, false));
        assertNull(query.cookie);
    }
}
