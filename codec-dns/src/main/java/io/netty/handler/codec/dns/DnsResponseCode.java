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
 * query. A response code of 0 indicates no error.
 */
public final class DnsResponseCode implements Comparable<DnsResponseCode> {

    /**
     * ID 0, no error
     */
    public static final DnsResponseCode NOERROR = new DnsResponseCode(0, "no error");

    /**
     * ID 1, format error
     */
    public static final DnsResponseCode FORMERROR = new DnsResponseCode(1, "format error");

    /**
     * ID 2, server failure
     */
    public static final DnsResponseCode SERVFAIL = new DnsResponseCode(2, "server failure");

    /**
     * ID 3, name error
     */
    public static final DnsResponseCode NXDOMAIN = new DnsResponseCode(3, "name error");

    /**
     * ID 4, not implemented
     */
    public static final DnsResponseCode NOTIMPL = new DnsResponseCode(4, "not implemented");

    /**
     * ID 5, operation refused
     */
    public static final DnsResponseCode REFUSED = new DnsResponseCode(5, "operation refused");

    /**
     * ID 6, domain name should not exist
     */
    public static final DnsResponseCode YXDOMAIN = new DnsResponseCode(6, "domain name should not exist");

    /**
     * ID 7, resource record set should not exist
     */
    public static final DnsResponseCode YXRRSET = new DnsResponseCode(7, "resource record set should not exist");

    /**
     * ID 8, rrset does not exist
     */
    public static final DnsResponseCode NXRRSET = new DnsResponseCode(8, "rrset does not exist");

    /**
     * ID 9, not authoritative for zone
     */
    public static final DnsResponseCode NOTAUTH = new DnsResponseCode(9, "not authoritative for zone");

    /**
     * ID 10, name not in zone
     */
    public static final DnsResponseCode NOTZONE = new DnsResponseCode(10, "name not in zone");

    /**
     * ID 11, bad extension mechanism for version
     */
    public static final DnsResponseCode BADVERS = new DnsResponseCode(11, "bad extension mechanism for version");

    /**
     * ID 12, bad signature
     */
    public static final DnsResponseCode BADSIG = new DnsResponseCode(12, "bad signature");

    /**
     * ID 13, bad key
     */
    public static final DnsResponseCode BADKEY = new DnsResponseCode(13, "bad key");

    /**
     * ID 14, bad timestamp
     */
    public static final DnsResponseCode BADTIME = new DnsResponseCode(14, "bad timestamp");

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
            case 11:
                return BADVERS;
            case 12:
                return BADSIG;
            case 13:
                return BADKEY;
            case 14:
                return BADTIME;
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
            return errorCode + "()";
        }
        return errorCode + '(' + message + ')';
    }
}
