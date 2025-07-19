/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

import io.netty.util.internal.ObjectUtil;

/**
 * The status of a SOCKS5 private authentication response.
 * <p>
 * RFC 1928 reserves method codes 0x80-0xFE for private authentication methods but does not
 * specify the format of their subnegotiation. This class provides standard status codes
 * for the private authentication response that follow the pattern established by the
 * username/password authentication method in RFC 1929.
 * </p>
 *
 * @see <a href="https://www.ietf.org/rfc/rfc1928.txt">RFC 1928 Section 3</a>
 * @see <a href="https://www.ietf.org/rfc/rfc1929.txt">RFC 1929</a>
 */
public class Socks5PrivateAuthStatus implements Comparable<Socks5PrivateAuthStatus> {

    public static final Socks5PrivateAuthStatus SUCCESS = new Socks5PrivateAuthStatus(0x00, "SUCCESS");
    public static final Socks5PrivateAuthStatus FAILURE = new Socks5PrivateAuthStatus(0xFF, "FAILURE");

    public static Socks5PrivateAuthStatus valueOf(byte b) {
        switch (b) {
        case 0x00:
            return SUCCESS;
        case (byte) 0xFF:
            return FAILURE;
        }

        return new Socks5PrivateAuthStatus(b);
    }

    private final byte byteValue;
    private final String name;
    private String text;

    public Socks5PrivateAuthStatus(int byteValue) {
        this(byteValue, "UNKNOWN");
    }

    public Socks5PrivateAuthStatus(int byteValue, String name) {
        this.name = ObjectUtil.checkNotNull(name, "name");
        this.byteValue = (byte) byteValue;
    }

    public byte byteValue() {
        return byteValue;
    }

    public boolean isSuccess() {
        return byteValue == 0;
    }

    @Override
    public int hashCode() {
        return byteValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Socks5PrivateAuthStatus)) {
            return false;
        }

        return byteValue == ((Socks5PrivateAuthStatus) obj).byteValue;
    }

    @Override
    public int compareTo(Socks5PrivateAuthStatus o) {
        return byteValue - o.byteValue;
    }

    @Override
    public String toString() {
        String text = this.text;
        if (text == null) {
            this.text = text = name + '(' + (byteValue & 0xFF) + ')';
        }
        return text;
    }
}
