/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.dns;

/**
 * Represents the possible response codes a server may send after receiving a
 * query, as described
 * <a href="http://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml">
 * by IANA</a>. A response code of 0 indicates no error.
 */
public final class DnsResponseCode implements Comparable<DnsResponseCode> {

    /**
     * ID 0, no error
     */
    public static final DnsResponseCode NOERROR = new DnsResponseCode(0, "No Error");

    /**
     * ID 1, format error
     */
    public static final DnsResponseCode FORMERROR = new DnsResponseCode(1, "Format Error");

    /**
     * ID 2, server failure
     */
    public static final DnsResponseCode SERVFAIL = new DnsResponseCode(2, "Server Failure");

    /**
     * ID 3, non-existent domain
     */
    public static final DnsResponseCode NXDOMAIN = new DnsResponseCode(3, "Non-Existent Domain");

    /**
     * ID 4, not implemented
     */
    public static final DnsResponseCode NOTIMPL = new DnsResponseCode(4, "Not Implemented");

    /**
     * ID 5, query refused
     */
    public static final DnsResponseCode REFUSED = new DnsResponseCode(5, "Query Refused");

    /**
     * ID 6, domain name should not exist but does
     */
    public static final DnsResponseCode YXDOMAIN = new DnsResponseCode(6, "Name Exists when it should not");

    /**
     * ID 7, resource record set should not exist but does
     */
    public static final DnsResponseCode YXRRSET = new DnsResponseCode(7, "RR Set Exists when it should not");

    /**
     * ID 8, rrset should exist but does not exist
     */
    public static final DnsResponseCode NXRRSET = new DnsResponseCode(8, "RR Set that should exist does not");

    /**
     * ID 9, not authoritative for zone
     */
    public static final DnsResponseCode NOTAUTH = new DnsResponseCode(9, "Server Not Authoritative for zone");

    /**
     * ID 10, name not in zone
     */
    public static final DnsResponseCode NOTZONE = new DnsResponseCode(10, "Name not contained in zone");

    /**
     * ID 16, bad extension mechanism for version.  <b>Note:</b>:
     * <a href="http://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml">
     * according to IANA</a> this has the same code as BADSIG, so results may be
     * ambiguous.
     */
    public static final DnsResponseCode BADVERS = new DnsResponseCode(16, "Bad OPT Version");

    /**
     * ID 16, TSIG Signature Failure.  Yes,
     * <a href="http://www.iana.org/assignments/dns-parameters/dns-parameters.xhtml">
     * according to IANA</a> this has the same code as BADVERS.
     *
     */
    public static final DnsResponseCode BADSIG = new DnsResponseCode(16, "TSIG Signature Failure");

    /**
     * ID 17, Key not recognized
     */
    public static final DnsResponseCode BADKEY = new DnsResponseCode(17, "Key not recognized");

    /**
     * ID 18, Signature out of time window
     */
    public static final DnsResponseCode BADTIME = new DnsResponseCode(18, "Signature out of time window");

    /**
     * ID 19, Bad TKEY Mode
     */
    public static final DnsResponseCode BADMODE = new DnsResponseCode(19, "Bad TKEY Mode");
    /**
     * ID 20, Duplicate Key Mode
     */
    public static final DnsResponseCode BADNAME = new DnsResponseCode(20, "Duplicate Key Name");
    /**
     * ID 21, Algorithm not supported
     */
    public static final DnsResponseCode BADALG = new DnsResponseCode(21, "Algorithm not supported");
    private final int errorCode;
    private final String message;

    /**
     * Returns the {@link DnsResponseCode} that corresponds with the given
     * {@code responseCode}.
     *
     * @param responseCode
     *            the error code's id
     * @return corresponding {@link DnsResponseCode} or {@code null} if none can be found.
     */
    public static DnsResponseCode valueOf(int responseCode) {
        switch (responseCode) {
            case 0:
                return NOERROR;
            case 1:
                return FORMERROR;
            case 2:
                return SERVFAIL;
            case 3:
                return NXDOMAIN;
            case 4:
                return NOTIMPL;
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
            // 11-15 are undefined per the spec
            case 16:
                return BADVERS;
            case 17:
                return BADSIG;
            case 18:
                return BADKEY;
            case 19:
                return BADTIME;
            case 20:
                return BADNAME;
            case 21:
                return BADALG;
            default:
                return new DnsResponseCode(responseCode, null);
        }
    }

    public DnsResponseCode(int errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    /**
     * Returns the error code for this {@link DnsResponseCode}.
     */
    public int code() {
        return errorCode;
    }

    @Override
    public int compareTo(DnsResponseCode o) {
        return code() - o.code();
    }

    @Override
    public int hashCode() {
        return code();
    }

    /**
     * Equality of {@link DnsResponseCode} only depends on {@link #code()}.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DnsResponseCode)) {
            return false;
        }

        return code() == ((DnsResponseCode) o).code();
    }

    /**
     * Returns a formatted error message for this {@link DnsResponseCode}.
     */
    @Override
    public String toString() {
        if (message == null) {
            return "DnsResponseCode(" + errorCode + ')';
        }
        return "DnsResponseCode(" + errorCode + ", " + message + ')';
    }
}
