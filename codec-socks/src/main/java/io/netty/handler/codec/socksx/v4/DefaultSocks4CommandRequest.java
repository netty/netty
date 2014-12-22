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

import io.netty.handler.codec.DecoderResult;
import io.netty.util.internal.StringUtil;

import java.net.IDN;

/**
 * The default {@link Socks4CommandRequest}.
 */
public class DefaultSocks4CommandRequest extends AbstractSocks4Message implements Socks4CommandRequest {

    private final Socks4CommandType type;
    private final String dstAddr;
    private final int dstPort;
    private final String userId;

    /**
     * Creates a new instance.
     *
     * @param type the type of the request
     * @param dstAddr the {@code DSTIP} field of the request
     * @param dstPort the {@code DSTPORT} field of the request
     */
    public DefaultSocks4CommandRequest(Socks4CommandType type, String dstAddr, int dstPort) {
        this(type, dstAddr, dstPort, "");
    }

    /**
     * Creates a new instance.
     *
     * @param type the type of the request
     * @param dstAddr the {@code DSTIP} field of the request
     * @param dstPort the {@code DSTPORT} field of the request
     * @param userId the {@code USERID} field of the request
     */
    public DefaultSocks4CommandRequest(Socks4CommandType type, String dstAddr, int dstPort, String userId) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (dstAddr == null) {
            throw new NullPointerException("dstAddr");
        }
        if (dstPort <= 0 || dstPort >= 65536) {
            throw new IllegalArgumentException("dstPort: " + dstPort + " (expected: 1~65535)");
        }
        if (userId == null) {
            throw new NullPointerException("userId");
        }

        this.userId = userId;
        this.type = type;
        this.dstAddr = IDN.toASCII(dstAddr);
        this.dstPort = dstPort;
    }

    @Override
    public Socks4CommandType type() {
        return type;
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
    public String userId() {
        return userId;
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
        buf.append(", dstAddr: ");
        buf.append(dstAddr());
        buf.append(", dstPort: ");
        buf.append(dstPort());
        buf.append(", userId: ");
        buf.append(userId());
        buf.append(')');

        return buf.toString();
    }
}
