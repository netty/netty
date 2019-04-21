/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.dns.record.opt;

import io.netty.util.collection.ShortObjectHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DNS EDNS0 Option Codes (OPT).
 *
 * @see <a href="https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml">
 * https://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml</a>
 */
public final class EDNS0OptionCode {
    /**
     * DNS Long-Lived Queries.
     *
     * @see <a href="http://files.dns-sd.org/draft-sekar-dns-llq.txt">
     * http://files.dns-sd.org/draft-sekar-dns-llq.txt</a>
     */
    public static final EDNS0OptionCode LLQ = new EDNS0OptionCode(0x1, "LLQ");

    /**
     * Dynamic DNS Update Leases.
     *
     * @see <a href="http://files.dns-sd.org/draft-sekar-dns-ul.txt">http://files.dns-sd.org/draft-sekar-dns-ul.txt</a>
     */
    public static final EDNS0OptionCode UL = new EDNS0OptionCode(0x2, "UL");

    /**
     * DNS Name Server Identifier (NSID) Option.
     *
     * @see <a href="https://tools.ietf.org/html/rfc5001">https://tools.ietf.org/html/rfc5001</a>
     */
    public static final EDNS0OptionCode NSID = new EDNS0OptionCode(0x3, "NSID");

    /**
     * Signaling Cryptographic Algorithm Understanding in DNS Security Extensions (DNSSEC).
     *
     * @see <a href="https://tools.ietf.org/html/rfc6975">https://tools.ietf.org/html/rfc6975</a>
     */
    public static final EDNS0OptionCode DAU = new EDNS0OptionCode(0x5, "DAU");

    /**
     * Signaling Cryptographic Algorithm Understanding in DNS Security Extensions (DNSSEC).
     *
     * @see <a href="https://tools.ietf.org/html/rfc6975">https://tools.ietf.org/html/rfc6975</a>
     */
    public static final EDNS0OptionCode DHU = new EDNS0OptionCode(0x6, "DHU");

    /**
     * Signaling Cryptographic Algorithm Understanding in DNS Security Extensions (DNSSEC).
     *
     * @see <a href="https://tools.ietf.org/html/rfc6975">https://tools.ietf.org/html/rfc6975</a>
     */
    public static final EDNS0OptionCode N3U = new EDNS0OptionCode(0x7, "N3U");

    /**
     * Client Subnet in DNS Queries.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7871">https://tools.ietf.org/html/rfc7871</a>
     */
    public static final EDNS0OptionCode SUBNET = new EDNS0OptionCode(0x8, "SUBNET");

    /**
     * Extension Mechanisms for DNS (EDNS) EXPIRE Option.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7314">https://tools.ietf.org/html/rfc7314</a>
     */
    public static final EDNS0OptionCode EXPIRE = new EDNS0OptionCode(0x9, "EXPIRE");

    /**
     * Domain Name System (DNS) Cookies.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7873">https://tools.ietf.org/html/rfc7873</a>
     */
    public static final EDNS0OptionCode COOKIE = new EDNS0OptionCode(0xa, "COOKIE");

    /**
     * The edns-tcp-keepalive EDNS0 Option.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7828">https://tools.ietf.org/html/rfc7828</a>
     */
    public static final EDNS0OptionCode TCPKEEPALIVE = new EDNS0OptionCode(0xb, "TCPKEEPALIVE");

    /**
     * The EDNS(0) Padding Option.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7830">https://tools.ietf.org/html/rfc7830</a>
     */
    public static final EDNS0OptionCode PADDING = new EDNS0OptionCode(0xc, "PADDING");


    /**
     * CHAIN Query Requests in DNS.
     *
     * @see <a href="https://tools.ietf.org/html/rfc7901">https://tools.ietf.org/html/rfc7901</a>
     */
    public static final EDNS0OptionCode CHAIN = new EDNS0OptionCode(0xd, "CHAIN");

    /**
     * Signaling Trust Anchor Knowledge in DNS Security Extensions (DNSSEC).
     *
     * @see <a href="https://tools.ietf.org/html/rfc8145">https://tools.ietf.org/html/rfc8145</a>
     */
    public static final EDNS0OptionCode KEYTAG = new EDNS0OptionCode(0xe, "KEYTAG");

    private static final String UNKNOWN_OPTION = "UNKNOWN";

    private static final Map<String, EDNS0OptionCode> BY_NAME = new HashMap<String, EDNS0OptionCode>();
    private static final ShortObjectHashMap<EDNS0OptionCode> BY_TYPE = new ShortObjectHashMap<EDNS0OptionCode>();
    private static final String EXPECTED;

    static {
        EDNS0OptionCode[] all = {
                LLQ, UL, NSID, DAU, DHU, N3U, SUBNET, EXPIRE, COOKIE, TCPKEEPALIVE, PADDING, CHAIN, KEYTAG
        };

        final StringBuilder expected = new StringBuilder(512);

        expected.append(" (expected: ");
        for (EDNS0OptionCode optionCode : all) {
            BY_NAME.put(optionCode.name, optionCode);
            BY_TYPE.put(optionCode.code, optionCode);
            expected.append(optionCode.name)
                    .append('(')
                    .append(optionCode.code)
                    .append("), ");
        }
        expected.setLength(expected.length() - 2);
        expected.append(')');
        EXPECTED = expected.toString();
    }

    public static EDNS0OptionCode valueOf(String name) {
        EDNS0OptionCode code = BY_NAME.get(name);
        if (code == null) {
            throw new IllegalArgumentException("name: " + name + EXPECTED);
        }
        return code;
    }

    public static EDNS0OptionCode valueOf(short codeValue) {
        EDNS0OptionCode optionCode = BY_TYPE.get(codeValue);
        if (optionCode == null) {
            return new EDNS0OptionCode(codeValue, UNKNOWN_OPTION);
        }
        return optionCode;
    }

    private final short code;
    private final String name;

    EDNS0OptionCode(int code, String name) {
        this.code = (short) code;
        this.name = name;
    }

    public short code() {
        return code;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EDNS0OptionCode that = (EDNS0OptionCode) o;
        return code == that.code &&
               Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, name);
    }

    @Override
    public String toString() {
        return String.format("(code: %x, name: %s)", code, name);
    }
}
