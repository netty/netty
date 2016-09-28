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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a {@link HAProxyTLV} of the type {@link io.netty.handler.codec.haproxy.HAProxyTLV.Type#PP2_TYPE_SSL}.
 * This TLV encapsulates other TLVs and has additional information like verification information and a client bitfield.
 */
public class HAProxySSLTLV extends HAProxyTLV {

    private final int verify;
    private final List<HAProxyTLV> tlvs;
    private final Set<CLIENT> clients;

    /**
     * The possible values of the clientfield bitmask
     */
    public enum CLIENT {
        PP2_CLIENT_SSL,
        PP2_CLIENT_CERT_CONN,
        PP2_CLIENT_CERT_SESS;
    }

    /**
     * Creates a new HAProxySSLTLV
     *
     * @param verify the verification result
     * @param clientBitField the bitfield with {@link CLIENT} information
     * @param tlvs the encapsulated {@link HAProxyTLV}s
     * @param rawContent the raw TLV content
     */
    HAProxySSLTLV(final int verify, final byte clientBitField, final List<HAProxyTLV> tlvs, final byte[] rawContent) {

        super(Type.PP2_TYPE_SSL, (byte) 0x20, rawContent);

        if (tlvs == null) {
            throw new NullPointerException("tlvs");
        }

        this.verify = verify;
        this.tlvs = tlvs;

        //Now parse the bitmask
        clients = new HashSet<CLIENT>();
        if ((clientBitField & 0x1) == 0x1) {

            clients.add(CLIENT.PP2_CLIENT_SSL);
        }
        if ((clientBitField & 0x2) == 0x2) {
            clients.add(CLIENT.PP2_CLIENT_CERT_CONN);
        }
        if ((clientBitField & 0x4) == 0x4) {

            clients.add(CLIENT.PP2_CLIENT_CERT_SESS);
        }
    }

    /**
     * Returns the verification result
     */
    public int getVerify() {
        return verify;
    }

    /**
     * Returns an unmodifiable Set of {@link CLIENT} values for this SSL TLV
     */
    public Set<CLIENT> getClients() {
        return Collections.unmodifiableSet(clients);
    }

    /**
     * Returns an unmodifiable Set of encapsulated {@link HAProxyTLV}s.
     */
    public List<HAProxyTLV> getEncapsulatedTLVs() {
        return Collections.unmodifiableList(tlvs);
    }

    @Override
    public boolean encapsulatesOtherTLVs() {
        //This TLV always encapsulates additional TLVs
        return true;
    }
}
