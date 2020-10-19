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
package io.netty.handler.codec.socksx.v4;

import io.netty.handler.codec.DecoderResult;
import io.netty.util.NetUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link Socks4CommandResponse}.
 */
public class DefaultSocks4CommandResponse extends AbstractSocks4Message implements Socks4CommandResponse {

    private final Socks4CommandStatus status;
    private final String dstAddr;
    private final int dstPort;

    /**
     * Creates a new instance.
     *
     * @param status the status of the response
     */
    public DefaultSocks4CommandResponse(Socks4CommandStatus status) {
        this(status, null, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param status the status of the response
     * @param dstAddr the {@code DSTIP} field of the response
     * @param dstPort the {@code DSTPORT} field of the response
     */
    public DefaultSocks4CommandResponse(Socks4CommandStatus status, String dstAddr, int dstPort) {
        if (dstAddr != null) {
            if (!NetUtil.isValidIpV4Address(dstAddr)) {
                throw new IllegalArgumentException(
                        "dstAddr: " + dstAddr + " (expected: a valid IPv4 address)");
            }
        }
        if (dstPort < 0 || dstPort > 65535) {
            throw new IllegalArgumentException("dstPort: " + dstPort + " (expected: 0~65535)");
        }

        this.status = ObjectUtil.checkNotNull(status, "cmdStatus");
        this.dstAddr = dstAddr;
        this.dstPort = dstPort;
    }

    @Override
    public Socks4CommandStatus status() {
        return status;
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
        StringBuilder buf = new StringBuilder(96);
        buf.append(StringUtil.simpleClassName(this));

        DecoderResult decoderResult = decoderResult();
        if (!decoderResult.isSuccess()) {
            buf.append("(decoderResult: ");
            buf.append(decoderResult);
            buf.append(", dstAddr: ");
        } else {
            buf.append("(dstAddr: ");
        }
        buf.append(dstAddr());
        buf.append(", dstPort: ");
        buf.append(dstPort());
        buf.append(')');

        return buf.toString();
    }
}
