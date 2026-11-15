/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * An ALPN protocol ID (<a href="https://www.rfc-editor.org/rfc/rfc7301.html#section-6">RFC 7301, Section 6</a>),
 * identified by its identification sequence: 1 to 255 octets, each held as a char in the range {@code [0, 255]}
 * (ISO-8859-1). The constants of this class are the instances returned for their identification sequences, so they
 * can be compared with {@code ==}.
 */
@UnstableApi
public final class ProtocolId {

    /**
     * {@code http/1.0}, HTTP/1.0.
     */
    public static final ProtocolId HTTP_1_0 = new ProtocolId("http/1.0");

    /**
     * {@code http/1.1}, HTTP/1.1.
     */
    public static final ProtocolId HTTP_1_1 = new ProtocolId("http/1.1");

    /**
     * {@code h2}, HTTP/2 over TLS.
     */
    public static final ProtocolId H2 = new ProtocolId("h2");

    /**
     * {@code h2c}, HTTP/2 over cleartext TCP.
     */
    public static final ProtocolId H2C = new ProtocolId("h2c");

    /**
     * {@code h3}, HTTP/3.
     */
    public static final ProtocolId H3 = new ProtocolId("h3");

    private static final ProtocolId[] KNOWN = { HTTP_1_0, HTTP_1_1, H2, H2C, H3 };

    /**
     * Returns the {@link ProtocolId} of the identification sequence {@code name}: the constant of this class for it,
     * a new instance otherwise.
     *
     * @param name the identification sequence, 1 to 255 chars long, each in the range {@code [0, 255]}
     * @throws IllegalArgumentException if {@code name} is not a valid identification sequence
     */
    public static ProtocolId valueOf(String name) {
        checkNotNull(name, "name");
        for (ProtocolId id : KNOWN) {
            if (id.name.equals(name)) {
                return id;
            }
        }
        if (name.isEmpty() || name.length() > 255) {
            throw new IllegalArgumentException("alpn-id length must be in the range [1, 255] but was " + name.length());
        }
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) > 0xFF) {
                throw new IllegalArgumentException(
                        "alpn-id char must be in the range [0, 255] but was " + (int) name.charAt(i));
            }
        }
        return new ProtocolId(name);
    }

    /**
     * Returns the {@link ProtocolId} of the {@code length} bytes of {@code in} at {@code index}, in the range
     * {@code [1, 255]}, without allocating for the identification sequence of a constant of this class.
     */
    static ProtocolId valueOf(ByteBuf in, int index, int length) {
        for (ProtocolId id : KNOWN) {
            if (id.matches(in, index, length)) {
                return id;
            }
        }
        return new ProtocolId(in.toString(index, length, CharsetUtil.ISO_8859_1));
    }

    private final String name;

    private ProtocolId(String name) {
        this.name = name;
    }

    /**
     * Returns the identification sequence, one char per octet.
     */
    public String name() {
        return name;
    }

    private boolean matches(ByteBuf in, int index, int length) {
        if (name.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (in.getByte(index + i) != (byte) name.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ProtocolId && name.equals(((ProtocolId) o).name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
