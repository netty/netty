/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.socksx.v4;

import io.netty.util.internal.ObjectUtil;

/**
 * The status of {@link Socks4CommandResponse}.
 */
public class Socks4CommandStatus implements Comparable<Socks4CommandStatus> {

    public static final Socks4CommandStatus SUCCESS = new Socks4CommandStatus(0x5a, "SUCCESS");
    public static final Socks4CommandStatus REJECTED_OR_FAILED = new Socks4CommandStatus(0x5b, "REJECTED_OR_FAILED");
    public static final Socks4CommandStatus IDENTD_UNREACHABLE = new Socks4CommandStatus(0x5c, "IDENTD_UNREACHABLE");
    public static final Socks4CommandStatus IDENTD_AUTH_FAILURE = new Socks4CommandStatus(0x5d, "IDENTD_AUTH_FAILURE");

    public static Socks4CommandStatus valueOf(byte b) {
        switch (b) {
        case 0x5a:
            return SUCCESS;
        case 0x5b:
            return REJECTED_OR_FAILED;
        case 0x5c:
            return IDENTD_UNREACHABLE;
        case 0x5d:
            return IDENTD_AUTH_FAILURE;
        }

        return new Socks4CommandStatus(b);
    }

    private final byte byteValue;
    private final String name;
    private String text;

    public Socks4CommandStatus(int byteValue) {
        this(byteValue, "UNKNOWN");
    }

    public Socks4CommandStatus(int byteValue, String name) {
        this.name = ObjectUtil.checkNotNull(name, "name");
        this.byteValue = (byte) byteValue;
    }

    public byte byteValue() {
        return byteValue;
    }

    public boolean isSuccess() {
        return byteValue == 0x5a;
    }

    @Override
    public int hashCode() {
        return byteValue;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Socks4CommandStatus)) {
            return false;
        }

        return byteValue == ((Socks4CommandStatus) obj).byteValue;
    }

    @Override
    public int compareTo(Socks4CommandStatus o) {
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
