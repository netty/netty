/*
 * Copyright 2012 The Netty Project
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

import io.netty.handler.codec.DecoderResult;
import io.netty.util.NetUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.net.IDN;

/**
 * The default {@link Socks5CommandRequest}.
 */
public final class DefaultSocks5CommandRequest extends AbstractSocks5Message implements Socks5CommandRequest {

    private final Socks5CommandType type;
    private final Socks5AddressType dstAddrType;
    private final String dstAddr;
    private final int dstPort;

    public DefaultSocks5CommandRequest(
            Socks5CommandType type, Socks5AddressType dstAddrType, String dstAddr, int dstPort) {

        this.type = ObjectUtil.checkNotNull(type, "type");
        ObjectUtil.checkNotNull(dstAddrType, "dstAddrType");
        ObjectUtil.checkNotNull(dstAddr, "dstAddr");

        if (dstAddrType == Socks5AddressType.IPv4) {
            if (!NetUtil.isValidIpV4Address(dstAddr)) {
                throw new IllegalArgumentException("dstAddr: " + dstAddr + " (expected: a valid IPv4 address)");
            }
        } else if (dstAddrType == Socks5AddressType.DOMAIN) {
            dstAddr = IDN.toASCII(dstAddr);
            if (dstAddr.length() > 255) {
                throw new IllegalArgumentException("dstAddr: " + dstAddr + " (expected: less than 256 chars)");
            }
        } else if (dstAddrType == Socks5AddressType.IPv6) {
            if (!NetUtil.isValidIpV6Address(dstAddr)) {
                throw new IllegalArgumentException("dstAddr: " + dstAddr + " (expected: a valid IPv6 address");
            }
        }

        if (dstPort < 0 || dstPort > 65535) {
            throw new IllegalArgumentException("dstPort: " + dstPort + " (expected: 0~65535)");
        }

        this.dstAddrType = dstAddrType;
        this.dstAddr = dstAddr;
        this.dstPort = dstPort;
    }

    @Override
    public Socks5CommandType type() {
        return type;
    }

    @Override
    public Socks5AddressType dstAddrType() {
        return dstAddrType;
    }

    @Override
    public String dstAddr() {
        return dstAddr;
    }

    @Override
    public int dstPort() {
        return dstPort;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(128);
        buf.append(StringUtil.simpleClassName(this));

        DecoderResult decoderResult = decoderResult();
        if (!decoderResult.isSuccess()) {
            buf.append("(decoderResult: ");
            buf.append(decoderResult);
            buf.append(", type: ");
        } else {
            buf.append("(type: ");
        }
        buf.append(type());
        buf.append(", dstAddrType: ");
        buf.append(dstAddrType());
        buf.append(", dstAddr: ");
        buf.append(dstAddr());
        buf.append(", dstPort: ");
        buf.append(dstPort());
        buf.append(')');

        return buf.toString();
    }
}
