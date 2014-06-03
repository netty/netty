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
public enum DnsResponseCode {

    /**
     * ID 0, no error
     */
    NOERROR(0, "no error"),

    /**
     * ID 1, format error
     */
    FORMERROR(1, "format error"),

    /**
     * ID 2, server failure
     */
    SERVFAIL(2, "server failure"),

    /**
     * ID 3, name error
     */
    NXDOMAIN(3, "name error"),

    /**
     * ID 4, not implemented
     */
    NOTIMPL(4, "not implemented"),

    /**
     * ID 5, operation refused
     */
    REFUSED(5, "operation refused"),

    /**
     * ID 6, domain name should not exist
     */
    YXDOMAIN(6, "domain name should not exist"),

    /**
     * ID 7, resource record set should not exist
     */
    YXRRSET(7, "resource record set should not exist"),

    /**
     * ID 8, rrset does not exist
     */
    NXRRSET(8, "rrset does not exist"),

    /**
     * ID 9, not authoritative for zone
     */
    NOTAUTH(9, "not authoritative for zone"),

    /**
     * ID 10, name not in zone
     */
    NOTZONE(10, "name not in zone"),

    /**
     * ID 11, bad extension mechanism for version
     */
    BADVERS(11, "bad extension mechanism for version"),

    /**
     * ID 12, bad signature
     */
    BADSIG(12, "bad signature"),

    /**
     * ID 13, bad key
     */
    BADKEY(13, "bad key"),

    /**
     * ID 14, bad timestamp
     */
    BADTIME(14, "bad timestamp");

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
        DnsResponseCode[] errors = DnsResponseCode.values();
        for (DnsResponseCode e : errors) {
            if (e.errorCode == responseCode) {
                return e;
            }
        }
        return null;
    }

    DnsResponseCode(int errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    /**
     * Returns the error code for this {@link DnsResponseCode}.
     */
    public int code() {
        return errorCode;
    }

    /**
     * Returns a formatted error message for this {@link DnsResponseCode}.
     */
    @Override
    public String toString() {
        return name() + ": type " + errorCode + ", " + message;
    }
}
