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
import io.netty.buffer.DefaultByteBufHolder;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * A Type-Length Value (TLV vector) that can be added to the PROXY protocol
 * to include additional information like SSL information.
 *
 * @see HAProxySSLTLV
 */
public class HAProxyTLV extends DefaultByteBufHolder {

    private final Type type;
    private final byte typeByteValue;

    /**
     * The registered types a TLV can have regarding the PROXY protocol 1.5 spec
     */
    public enum Type {
        PP2_TYPE_ALPN,
        PP2_TYPE_AUTHORITY,
        PP2_TYPE_SSL,
        PP2_TYPE_SSL_VERSION,
        PP2_TYPE_SSL_CN,
        PP2_TYPE_NETNS,
        /**
         * A TLV type that is not officially defined in the spec. May be used for nonstandard TLVs
         */
        OTHER;

        /**
         * Returns the {@link Type} for a specific byte value as defined in the PROXY protocol 1.5 spec
         * <p>
         * If the byte value is not an official one, it will return {@link Type#OTHER}.
         *
         * @param byteValue the byte for a type
         *
         * @return the {@link Type} of a TLV
         */
        public static Type typeForByteValue(final byte byteValue) {
            switch (byteValue) {
            case 0x01:
                return PP2_TYPE_ALPN;
            case 0x02:
                return PP2_TYPE_AUTHORITY;
            case 0x20:
                return PP2_TYPE_SSL;
            case 0x21:
                return PP2_TYPE_SSL_VERSION;
            case 0x22:
                return PP2_TYPE_SSL_CN;
            case 0x30:
                return PP2_TYPE_NETNS;
            default:
                return OTHER;
            }
        }
    }

    /**
     * Creates a new HAProxyTLV
     *
     * @param type the {@link Type} of the TLV
     * @param typeByteValue the byteValue of the TLV. This is especially important if non-standard TLVs are used
     * @param content the raw content of the TLV
     */
    HAProxyTLV(final Type type, final byte typeByteValue, final ByteBuf content) {
        super(content);
        checkNotNull(type, "type");

        this.type = type;
        this.typeByteValue = typeByteValue;
    }

    /**
     * Returns the {@link Type} of this TLV
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the type of the TLV as byte
     */
    public byte typeByteValue() {
        return typeByteValue;
    }

    @Override
    public HAProxyTLV copy() {
        return replace(content().copy());
    }

    @Override
    public HAProxyTLV duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public HAProxyTLV retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public HAProxyTLV replace(ByteBuf content) {
        return new HAProxyTLV(type, typeByteValue, content);
    }

    @Override
    public HAProxyTLV retain() {
        super.retain();
        return this;
    }

    @Override
    public HAProxyTLV retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public HAProxyTLV touch() {
        super.touch();
        return this;
    }

    @Override
    public HAProxyTLV touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
