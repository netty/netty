/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The DNS {@code RCODE}, as defined in <a href="https://tools.ietf.org/html/rfc2929">RFC2929</a>.
 */
@UnstableApi
public class DnsResponseCode implements Comparable<DnsResponseCode> {

    /**
     * The 'NoError' DNS RCODE (0), as defined in <a href="https://tools.ietf.org/html/rfc1035">RFC1035</a>.
     */
    public static final DnsResponseCode NOERROR = new DnsResponseCode(0, "NoError");

    /**
     * The 'FormErr' DNS RCODE (1), as defined in <a href="https://tools.ietf.org/html/rfc1035">RFC1035</a>.
     */
    public static final DnsResponseCode FORMERR = new DnsResponseCode(1, "FormErr");

    /**
     * The 'ServFail' DNS RCODE (2), as defined in <a href="https://tools.ietf.org/html/rfc1035">RFC1035</a>.
     */
    public static final DnsResponseCode SERVFAIL = new DnsResponseCode(2, "ServFail");

    /**
     * The 'NXDomain' DNS RCODE (3), as defined in <a href="https://tools.ietf.org/html/rfc1035">RFC1035</a>.
     */
    public static final DnsResponseCode NXDOMAIN = new DnsResponseCode(3, "NXDomain");

    /**
     * The 'NotImp' DNS RCODE (4), as defined in <a href="https://tools.ietf.org/html/rfc1035">RFC1035</a>.
     */
    public static final DnsResponseCode NOTIMP = new DnsResponseCode(4, "NotImp");

    /**
     * The 'Refused' DNS RCODE (5), as defined in <a href="https://tools.ietf.org/html/rfc1035">RFC1035</a>.
     */
    public static final DnsResponseCode REFUSED = new DnsResponseCode(5, "Refused");

    /**
     * The 'YXDomain' DNS RCODE (6), as defined in <a href="https://tools.ietf.org/html/rfc2136">RFC2136</a>.
     */
    public static final DnsResponseCode YXDOMAIN = new DnsResponseCode(6, "YXDomain");

    /**
     * The 'YXRRSet' DNS RCODE (7), as defined in <a href="https://tools.ietf.org/html/rfc2136">RFC2136</a>.
     */
    public static final DnsResponseCode YXRRSET = new DnsResponseCode(7, "YXRRSet");

    /**
     * The 'NXRRSet' DNS RCODE (8), as defined in <a href="https://tools.ietf.org/html/rfc2136">RFC2136</a>.
     */
    public static final DnsResponseCode NXRRSET = new DnsResponseCode(8, "NXRRSet");

    /**
     * The 'NotAuth' DNS RCODE (9), as defined in <a href="https://tools.ietf.org/html/rfc2136">RFC2136</a>.
     */
    public static final DnsResponseCode NOTAUTH = new DnsResponseCode(9, "NotAuth");

    /**
     * The 'NotZone' DNS RCODE (10), as defined in <a href="https://tools.ietf.org/html/rfc2136">RFC2136</a>.
     */
    public static final DnsResponseCode NOTZONE = new DnsResponseCode(10, "NotZone");

    /**
     * The 'BADVERS' or 'BADSIG' DNS RCODE (16), as defined in <a href="https://tools.ietf.org/html/rfc2671">RFC2671</a>
     * and <a href="https://tools.ietf.org/html/rfc2845">RFC2845</a>.
     */
    public static final DnsResponseCode BADVERS_OR_BADSIG = new DnsResponseCode(16, "BADVERS_OR_BADSIG");

    /**
     * The 'BADKEY' DNS RCODE (17), as defined in <a href="https://tools.ietf.org/html/rfc2845">RFC2845</a>.
     */
    public static final DnsResponseCode BADKEY = new DnsResponseCode(17, "BADKEY");

    /**
     * The 'BADTIME' DNS RCODE (18), as defined in <a href="https://tools.ietf.org/html/rfc2845">RFC2845</a>.
     */
    public static final DnsResponseCode BADTIME = new DnsResponseCode(18, "BADTIME");

    /**
     * The 'BADMODE' DNS RCODE (19), as defined in <a href="https://tools.ietf.org/html/rfc2930">RFC2930</a>.
     */
    public static final DnsResponseCode BADMODE = new DnsResponseCode(19, "BADMODE");

    /**
     * The 'BADNAME' DNS RCODE (20), as defined in <a href="https://tools.ietf.org/html/rfc2930">RFC2930</a>.
     */
    public static final DnsResponseCode BADNAME = new DnsResponseCode(20, "BADNAME");

    /**
     * The 'BADALG' DNS RCODE (21), as defined in <a href="https://tools.ietf.org/html/rfc2930">RFC2930</a>.
     */
    public static final DnsResponseCode BADALG = new DnsResponseCode(21, "BADALG");

    /**
     * Returns the {@link DnsResponseCode} that corresponds with the given {@code responseCode}.
     *
     * @param responseCode the DNS RCODE
     *
     * @return the corresponding {@link DnsResponseCode}
     */
    public static DnsResponseCode valueOf(int responseCode) {
        switch (responseCode) {
        case 0:
            return NOERROR;
        case 1:
            return FORMERR;
        case 2:
            return SERVFAIL;
        case 3:
            return NXDOMAIN;
        case 4:
            return NOTIMP;
        case 5:
            return REFUSED;
        case 6:
            return YXDOMAIN;
        case 7:
            return YXRRSET;
        case 8:
            return NXRRSET;
        case 9:
            return NOTAUTH;
        case 10:
            return NOTZONE;
        case 16:
            return BADVERS_OR_BADSIG;
        case 17:
            return BADKEY;
        case 18:
            return BADTIME;
        case 19:
            return BADMODE;
        case 20:
            return BADNAME;
        case 21:
            return BADALG;
        default:
            return new DnsResponseCode(responseCode);
        }
    }

    private final int code;
    private final String name;
    private String text;

    private DnsResponseCode(int code) {
        this(code, "UNKNOWN");
    }

    public DnsResponseCode(int code, String name) {
        if (code < 0 || code > 65535) {
            throw new IllegalArgumentException("code: " + code + " (expected: 0 ~ 65535)");
        }

        this.code = code;
        this.name = checkNotNull(name, "name");
    }

    /**
     * Returns the error code for this {@link DnsResponseCode}.
     */
    public int intValue() {
        return code;
    }

    @Override
    public int compareTo(DnsResponseCode o) {
        return intValue() - o.intValue();
    }

    @Override
    public int hashCode() {
        return intValue();
    }

    /**
     * Equality of {@link DnsResponseCode} only depends on {@link #intValue()}.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DnsResponseCode)) {
            return false;
        }

        return intValue() == ((DnsResponseCode) o).intValue();
    }

    /**
     * Returns a formatted error message for this {@link DnsResponseCode}.
     */
    @Override
    public String toString() {
        String text = this.text;
        if (text == null) {
            this.text = text = name + '(' + intValue() + ')';
        }
        return text;
    }
}
