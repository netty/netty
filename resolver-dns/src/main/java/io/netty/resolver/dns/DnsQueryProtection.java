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
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsOptCookieRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defences that make it harder for an off-path attacker to spoof the response of a UDP DNS query on top of the
 * query id and the source port, as recommended by
 * <a href="https://tools.ietf.org/html/rfc5452">RFC 5452</a>:
 * <ul>
 *     <li>"0x20" randomization of the case of the name in the question
 *     (<a href="https://datatracker.ietf.org/doc/html/draft-vixie-dnsext-dns0x20-00">draft-vixie-dnsext-dns0x20</a>)
 *     </li>
 *     <li>client cookies as defined in <a href="https://tools.ietf.org/html/rfc7873">RFC 7873</a></li>
 * </ul>
 */
final class DnsQueryProtection {

    /**
     * Upper bound for the number of nameservers we remember to support cookies. Once reached we stop remembering
     * new ones, which means that responses from those will not be rejected if they omit the cookie.
     */
    private static final int MAX_COOKIE_SERVERS = 1024;

    private static final String ACE_PREFIX = "xn--";

    /**
     * The result of checking the cookie in a response.
     */
    enum CookieStatus {
        /**
         * The response contained no cookie.
         */
        ABSENT,
        /**
         * The response contained a cookie that echoes the client cookie.
         */
        VALID,
        /**
         * The response contained a cookie that does not echo the client cookie or is malformed.
         */
        INVALID
    }

    private final SecureRandom random = new SecureRandom();
    private final boolean caseRandomization;
    // null if cookies are disabled.
    private final Set<InetSocketAddress> cookieServers;

    DnsQueryProtection(boolean caseRandomization, boolean cookies) {
        this.caseRandomization = caseRandomization;
        this.cookieServers = cookies ?
                Collections.newSetFromMap(new ConcurrentHashMap<InetSocketAddress, Boolean>()) : null;
    }

    boolean isCaseRandomizationEnabled() {
        return caseRandomization;
    }

    boolean isCookiesEnabled() {
        return cookieServers != null;
    }

    /**
     * Returns a copy of the given {@link DnsQuestion} in which the case of every ASCII letter was picked at random.
     */
    DnsQuestion randomizeCase(DnsQuestion question) {
        final String name = question.name();
        final int length = name.length();
        final char[] chars = name.toCharArray();
        final byte[] bits = new byte[(length + 7) >>> 3];
        random.nextBytes(bits);

        int labelStart = 0;
        while (labelStart < length) {
            int labelEnd = name.indexOf('.', labelStart);
            if (labelEnd == -1) {
                labelEnd = length;
            }
            // Don't touch labels that carry punycode, so they stay valid for IDN.toASCII(...).
            if (!name.regionMatches(true, labelStart, ACE_PREFIX, 0, ACE_PREFIX.length())) {
                for (int i = labelStart; i < labelEnd; i++) {
                    char c = chars[i];
                    if (((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) && (bits[i >>> 3] >>> (i & 7) & 1) != 0) {
                        // Toggle the case bit.
                        chars[i] = (char) (c ^ 0x20);
                    }
                }
            }
            labelStart = labelEnd + 1;
        }
        return new DefaultDnsQuestion(new String(chars), question.type(), question.dnsClass());
    }

    byte[] newClientCookie() {
        byte[] cookie = new byte[DnsOptCookieRecord.CLIENT_COOKIE_LENGTH];
        random.nextBytes(cookie);
        return cookie;
    }

    /**
     * Returns {@code true} if the given nameserver has returned a valid cookie before, and so is expected to do so
     * for every response.
     */
    boolean isCookieExpected(InetSocketAddress nameServerAddr) {
        return cookieServers != null && cookieServers.contains(nameServerAddr);
    }

    void cookieReceived(InetSocketAddress nameServerAddr) {
        if (cookieServers != null && cookieServers.size() < MAX_COOKIE_SERVERS) {
            cookieServers.add(nameServerAddr);
        }
    }

    /**
     * Inspect the COOKIE option of the OPT record of the response.
     */
    static CookieStatus checkCookie(DnsResponse response, byte[] clientCookie) {
        final int count = response.count(DnsSection.ADDITIONAL);
        for (int i = 0; i < count; i++) {
            DnsRecord record = response.recordAt(DnsSection.ADDITIONAL, i);
            if (record.type() == DnsRecordType.OPT) {
                // There must be only one OPT record, so just look at the first.
                if (!(record instanceof DnsRawRecord)) {
                    return CookieStatus.INVALID;
                }
                return checkCookie(((DnsRawRecord) record).content(), clientCookie);
            }
        }
        return CookieStatus.ABSENT;
    }

    private static CookieStatus checkCookie(ByteBuf rdata, byte[] clientCookie) {
        // See https://tools.ietf.org/html/rfc6891#section-6.1.2 for the layout of the options.
        int index = rdata.readerIndex();
        final int end = rdata.writerIndex();
        CookieStatus status = CookieStatus.ABSENT;
        while (index < end) {
            if (end - index < 4) {
                return CookieStatus.INVALID;
            }
            final int code = rdata.getUnsignedShort(index);
            final int length = rdata.getUnsignedShort(index + 2);
            index += 4;
            if (end - index < length) {
                return CookieStatus.INVALID;
            }
            if (code == DnsOptCookieRecord.OPTION_CODE) {
                if (status != CookieStatus.ABSENT) {
                    // Only one cookie option is allowed.
                    return CookieStatus.INVALID;
                }
                // The client cookie may be followed by a server cookie which is 8 to 32 bytes long.
                if (length != clientCookie.length && (length < clientCookie.length + 8 ||
                        length > clientCookie.length + 32)) {
                    return CookieStatus.INVALID;
                }
                status = CookieStatus.VALID;
                // Compare in constant time, just to be on the safe side.
                int diff = 0;
                for (int i = 0; i < clientCookie.length; i++) {
                    diff |= rdata.getByte(index + i) ^ clientCookie[i];
                }
                if (diff != 0) {
                    return CookieStatus.INVALID;
                }
            }
            index += length;
        }
        return status;
    }
}
