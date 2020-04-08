/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec.haproxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.StringUtil;

import java.util.Collections;
import java.util.List;

/**
 * Represents a {@link HAProxyTLV} of the type {@link HAProxyTLV.Type#PP2_TYPE_SSL}.
 * This TLV encapsulates other TLVs and has additional information like verification information and a client bitfield.
 */
public final class HAProxySSLTLV extends HAProxyTLV {

    private final int verify;
    private final List<HAProxyTLV> tlvs;
    private final byte clientBitField;

    /**
     * Creates a new HAProxySSLTLV
     *
     * @param verify the verification result as defined in the specification for the pp2_tlv_ssl struct (see
     * http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)
     * @param clientBitField the bitfield with client information
     * @param tlvs the encapsulated {@link HAProxyTLV}s
     */
    public HAProxySSLTLV(final int verify, final byte clientBitField, final List<HAProxyTLV> tlvs) {
        this(verify, clientBitField, tlvs, Unpooled.EMPTY_BUFFER);
    }

    /**
     * Creates a new HAProxySSLTLV
     *
     * @param verify the verification result as defined in the specification for the pp2_tlv_ssl struct (see
     * http://www.haproxy.org/download/1.8/doc/proxy-protocol.txt)
     * @param clientBitField the bitfield with client information
     * @param tlvs the encapsulated {@link HAProxyTLV}s
     * @param rawContent the raw TLV content
     */
    HAProxySSLTLV(final int verify, final byte clientBitField, final List<HAProxyTLV> tlvs, final ByteBuf rawContent) {
        super(Type.PP2_TYPE_SSL, (byte) 0x20, rawContent);

        this.verify = verify;
        this.tlvs = Collections.unmodifiableList(tlvs);
        this.clientBitField = clientBitField;
    }

    /**
     * Returns {@code true} if the bit field for PP2_CLIENT_CERT_CONN was set
     */
    public boolean isPP2ClientCertConn() {
        return (clientBitField & 0x2) != 0;
    }

    /**
     * Returns {@code true} if the bit field for PP2_CLIENT_SSL was set
     */
    public boolean isPP2ClientSSL() {
        return (clientBitField & 0x1) != 0;
    }

    /**
     * Returns {@code true} if the bit field for PP2_CLIENT_CERT_SESS was set
     */
    public boolean isPP2ClientCertSess() {
        return (clientBitField & 0x4) != 0;
    }

    /**
     * Returns the client bit field
     */
    public byte client() {
        return clientBitField;
    }

    /**
     * Returns the verification result
     */
    public int verify() {
        return verify;
    }

    /**
     * Returns an unmodifiable Set of encapsulated {@link HAProxyTLV}s.
     */
    public List<HAProxyTLV> encapsulatedTLVs() {
        return tlvs;
    }

    @Override
    int contentNumBytes() {
        int tlvNumBytes = 0;
        for (int i = 0; i < tlvs.size(); i++) {
            tlvNumBytes += tlvs.get(i).totalNumBytes();
        }
        return 5 + tlvNumBytes; // clientBit(1) + verify(4) + tlvs
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) +
               "(type: " + type() +
               ", typeByteValue: " + typeByteValue() +
               ", client: " + client() +
               ", verify: " + verify() +
               ", numEncapsulatedTlvs: " + tlvs.size() + ')';
    }
}
