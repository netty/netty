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

package io.netty.handler.codec.socksx.v5;

import io.netty.util.internal.ObjectUtil;

/**
 * The status of {@link Socks5CommandResponse}.
 */
public class Socks5CommandStatus implements Comparable<Socks5CommandStatus> {

    public static final Socks5CommandStatus SUCCESS = new Socks5CommandStatus(0x00, "SUCCESS");
    public static final Socks5CommandStatus FAILURE = new Socks5CommandStatus(0x01, "FAILURE");
    public static final Socks5CommandStatus FORBIDDEN = new Socks5CommandStatus(0x02, "FORBIDDEN");
    public static final Socks5CommandStatus NETWORK_UNREACHABLE = new Socks5CommandStatus(0x03, "NETWORK_UNREACHABLE");
    public static final Socks5CommandStatus HOST_UNREACHABLE = new Socks5CommandStatus(0x04, "HOST_UNREACHABLE");
    public static final Socks5CommandStatus CONNECTION_REFUSED = new Socks5CommandStatus(0x05, "CONNECTION_REFUSED");
    public static final Socks5CommandStatus TTL_EXPIRED = new Socks5CommandStatus(0x06, "TTL_EXPIRED");
    public static final Socks5CommandStatus COMMAND_UNSUPPORTED = new Socks5CommandStatus(0x07, "COMMAND_UNSUPPORTED");
    public static final Socks5CommandStatus ADDRESS_UNSUPPORTED = new Socks5CommandStatus(0x08, "ADDRESS_UNSUPPORTED");

    public static Socks5CommandStatus valueOf(byte b) {
        switch (b) {
        case 0x00:
            return SUCCESS;
        case 0x01:
            return FAILURE;
        case 0x02:
            return FORBIDDEN;
        case 0x03:
            return NETWORK_UNREACHABLE;
        case 0x04:
            return HOST_UNREACHABLE;
        case 0x05:
            return CONNECTION_REFUSED;
        case 0x06:
            return TTL_EXPIRED;
        case 0x07:
            return COMMAND_UNSUPPORTED;
        case 0x08:
            return ADDRESS_UNSUPPORTED;
        }

        return new Socks5CommandStatus(b);
    }

    private final byte byteValue;
    private final String name;
    private String text;

    public Socks5CommandStatus(int byteValue) {
        this(byteValue, "UNKNOWN");
    }

    public Socks5CommandStatus(int byteValue, String name) {
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
        if (!(obj instanceof Socks5CommandStatus)) {
            return false;
        }

        return byteValue == ((Socks5CommandStatus) obj).byteValue;
    }

    @Override
    public int compareTo(Socks5CommandStatus o) {
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
