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
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AsciiString;
import io.netty.util.NetUtil;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A service parameter (SvcParam) of a {@link DnsSvcbRecord}, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9460.html">RFC 9460</a>.
 */
public abstract class SvcParam {

    /**
     * The {@code mandatory} SvcParamKey.
     */
    public static final int MANDATORY = 0;

    /**
     * The {@code alpn} SvcParamKey.
     */
    public static final int ALPN = 1;

    /**
     * The {@code no-default-alpn} SvcParamKey.
     */
    public static final int NO_DEFAULT_ALPN = 2;

    /**
     * The {@code port} SvcParamKey.
     */
    public static final int PORT = 3;

    /**
     * The {@code ipv4hint} SvcParamKey.
     */
    public static final int IPV4HINT = 4;

    /**
     * The {@code ech} SvcParamKey.
     */
    public static final int ECH = 5;

    /**
     * The {@code ipv6hint} SvcParamKey.
     */
    public static final int IPV6HINT = 6;

    /**
     * The SvcParamKey reserved as "Invalid key" by RFC 9460. The RFC defines no special handling for it: it is
     * accepted and represented like any other key without a dedicated class, as {@link Unknown}. Interpreting it
     * is left to the code using the record.
     */
    public static final int INVALID_KEY = 65535;

    private final int key;

    /**
     * Creates a new instance.
     *
     * @param key the SvcParamKey, in the range {@code [0, 65535]}
     */
    public SvcParam(int key) {
        if (key < 0 || key > 65535) {
            throw new IllegalArgumentException("key must be in the range [0, 65535]");
        }
        this.key = key;
    }

    /**
     * The {@code mandatory} parameter: the keys a client must support to use the record.
     */
    public static final class Mandatory extends SvcParam {

        private final List<Integer> keys;

        /**
         * Creates a new instance.
         *
         * @param keys the mandatory keys, non-empty, without duplicates and not containing {@link #MANDATORY}.
         *             They are stored in increasing order.
         */
        public Mandatory(List<Integer> keys) {
            super(MANDATORY);
            List<Integer> copy = new ArrayList<Integer>(checkNotNull(keys, "keys"));
            if (copy.isEmpty()) {
                throw new IllegalArgumentException("keys must not be empty");
            }
            for (Integer k : copy) {
                checkNotNull(k, "key");
                if (k < 0 || k > 65535) {
                    throw new IllegalArgumentException("key must be in the range [0, 65535] but was " + k);
                }
                if (k == MANDATORY) {
                    throw new IllegalArgumentException("mandatory must not list itself");
                }
            }
            Collections.sort(copy);
            for (int i = 1; i < copy.size(); i++) {
                if (copy.get(i).equals(copy.get(i - 1))) {
                    throw new IllegalArgumentException("duplicate mandatory key: " + copy.get(i));
                }
            }
            this.keys = Collections.unmodifiableList(copy);
        }

        /**
         * Returns the mandatory keys, in increasing order.
         */
        public List<Integer> keys() {
            return keys;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Mandatory && keys.equals(((Mandatory) o).keys);
        }

        @Override
        public int hashCode() {
            return MANDATORY * 31 + keys.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("mandatory=");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    buf.append(',');
                }
                buf.append(keyName(keys.get(i)));
            }
            return buf.toString();
        }

        @Override
        protected void encodeValue(ByteBuf out) {
            for (Integer k : keys) {
                out.writeShort(k);
            }
        }
    }

    /**
     * The {@code alpn} parameter: the ALPN protocol identifiers supported by the service.
     */
    public static final class Alpn extends SvcParam {

        private final List<AsciiString> protocols;

        /**
         * Creates a new instance.
         *
         * @param protocols the ALPN protocol identifiers, non-empty, each 1 to 255 bytes long
         */
        public Alpn(List<AsciiString> protocols) {
            super(ALPN);
            List<AsciiString> copy = new ArrayList<AsciiString>(checkNotNull(protocols, "protocols"));
            if (copy.isEmpty()) {
                throw new IllegalArgumentException("protocols must not be empty");
            }
            for (AsciiString protocol : copy) {
                checkNotNull(protocol, "protocol");
                if (protocol.isEmpty() || protocol.length() > 255) {
                    throw new IllegalArgumentException(
                            "ALPN protocol length must be in the range [1, 255] but was " + protocol.length());
                }
            }
            this.protocols = Collections.unmodifiableList(copy);
        }

        /**
         * Returns the ALPN protocol identifiers, in record order.
         */
        public List<AsciiString> protocols() {
            return protocols;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Alpn && protocols.equals(((Alpn) o).protocols);
        }

        @Override
        public int hashCode() {
            return ALPN * 31 + protocols.hashCode();
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder("alpn=");
            for (int i = 0; i < protocols.size(); i++) {
                if (i > 0) {
                    buf.append(',');
                }
                buf.append(protocols.get(i));
            }
            return buf.toString();
        }

        @Override
        protected void encodeValue(ByteBuf out) {
            for (AsciiString protocol : protocols) {
                out.writeByte(protocol.length());
                out.writeBytes(protocol.array(), protocol.arrayOffset(), protocol.length());
            }
        }
    }

    /**
     * The {@code no-default-alpn} parameter, which has an empty value.
     */
    public static final class NoDefaultAlpn extends SvcParam {

        /**
         * The {@code no-default-alpn} parameter.
         */
        public static final NoDefaultAlpn INSTANCE = new NoDefaultAlpn();

        private NoDefaultAlpn() {
            super(NO_DEFAULT_ALPN);
        }

        @Override
        public String toString() {
            return "no-default-alpn";
        }
    }

    /**
     * The {@code port} parameter: the TCP or UDP port of the service.
     */
    public static final class Port extends SvcParam {

        private final int port;

        /**
         * Creates a new instance.
         *
         * @param port the port, in the range {@code [0, 65535]}
         */
        public Port(int port) {
            super(PORT);
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("port must be in the range [0, 65535] but was " + port);
            }
            this.port = port;
        }

        /**
         * Returns the port.
         */
        public int port() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Port && port == ((Port) o).port;
        }

        @Override
        public int hashCode() {
            return PORT * 31 + port;
        }

        @Override
        public String toString() {
            return "port=" + port;
        }

        @Override
        protected void encodeValue(ByteBuf out) {
            out.writeShort(port);
        }
    }

    /**
     * The {@code ipv4hint} parameter: IPv4 addresses that may be used to reach the service.
     */
    public static final class Ipv4Hint extends SvcParam {

        private final List<Inet4Address> addresses;

        /**
         * Creates a new instance.
         *
         * @param addresses the addresses, non-empty
         */
        public Ipv4Hint(List<Inet4Address> addresses) {
            super(IPV4HINT);
            this.addresses = copyAddresses(addresses);
        }

        /**
         * Returns the addresses, in record order.
         */
        public List<Inet4Address> addresses() {
            return addresses;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ipv4Hint && addresses.equals(((Ipv4Hint) o).addresses);
        }

        @Override
        public int hashCode() {
            return IPV4HINT * 31 + addresses.hashCode();
        }

        @Override
        public String toString() {
            return addressesToString("ipv4hint=", addresses);
        }

        @Override
        protected void encodeValue(ByteBuf out) {
            for (Inet4Address address : addresses) {
                out.writeBytes(address.getAddress());
            }
        }
    }

    /**
     * The {@code ech} parameter: an opaque ECHConfigList.
     */
    public static final class Ech extends SvcParam {

        private final byte[] echConfigList;

        /**
         * Creates a new instance.
         *
         * @param echConfigList the ECHConfigList bytes, which are copied
         */
        public Ech(byte[] echConfigList) {
            super(ECH);
            this.echConfigList = checkNotNull(echConfigList, "echConfigList").clone();
        }

        /**
         * Returns a copy of the ECHConfigList bytes.
         */
        public byte[] echConfigList() {
            return echConfigList.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ech && Arrays.equals(echConfigList, ((Ech) o).echConfigList);
        }

        @Override
        public int hashCode() {
            return ECH * 31 + Arrays.hashCode(echConfigList);
        }

        @Override
        public String toString() {
            return "ech=" + ByteBufUtil.hexDump(echConfigList);
        }

        @Override
        protected void encodeValue(ByteBuf out) {
            out.writeBytes(echConfigList);
        }
    }

    /**
     * The {@code ipv6hint} parameter: IPv6 addresses that may be used to reach the service.
     */
    public static final class Ipv6Hint extends SvcParam {

        private final List<Inet6Address> addresses;

        /**
         * Creates a new instance.
         *
         * @param addresses the addresses, non-empty
         */
        public Ipv6Hint(List<Inet6Address> addresses) {
            super(IPV6HINT);
            this.addresses = copyAddresses(addresses);
        }

        /**
         * Returns the addresses, in record order.
         */
        public List<Inet6Address> addresses() {
            return addresses;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Ipv6Hint && addresses.equals(((Ipv6Hint) o).addresses);
        }

        @Override
        public int hashCode() {
            return IPV6HINT * 31 + addresses.hashCode();
        }

        @Override
        public String toString() {
            return addressesToString("ipv6hint=", addresses);
        }

        @Override
        protected void encodeValue(ByteBuf out) {
            for (Inet6Address address : addresses) {
                out.writeBytes(address.getAddress());
            }
        }
    }

    /**
     * A parameter with a key that has no dedicated class, holding its value as opaque bytes. This includes
     * private-use keys and the reserved {@link #INVALID_KEY}.
     */
    public static final class Unknown extends SvcParam {

        private final byte[] value;

        /**
         * Creates a new instance.
         *
         * @param key the SvcParamKey, in the range {@code [0, 65535]}
         * @param value the SvcParamValue bytes, which are copied
         */
        public Unknown(int key, byte[] value) {
            super(key);
            this.value = checkNotNull(value, "value").clone();
        }

        /**
         * Returns a copy of the SvcParamValue bytes.
         */
        public byte[] value() {
            return value.clone();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Unknown)) {
                return false;
            }
            Unknown that = (Unknown) o;
            return key() == that.key() && Arrays.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return key() * 31 + Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "key" + key() + '=' + ByteBufUtil.hexDump(value);
        }

        @Override
        protected void encodeValue(ByteBuf out) {
            out.writeBytes(value);
        }
    }

    private static String keyName(int key) {
        switch (key) {
            case MANDATORY:
                return "mandatory";
            case ALPN:
                return "alpn";
            case NO_DEFAULT_ALPN:
                return "no-default-alpn";
            case PORT:
                return "port";
            case IPV4HINT:
                return "ipv4hint";
            case ECH:
                return "ech";
            case IPV6HINT:
                return "ipv6hint";
            default:
                return "key" + key;
        }
    }

    private static String addressesToString(String prefix, List<? extends InetAddress> addresses) {
        StringBuilder buf = new StringBuilder(prefix);
        for (int i = 0; i < addresses.size(); i++) {
            if (i > 0) {
                buf.append(',');
            }
            buf.append(NetUtil.toAddressString(addresses.get(i)));
        }
        return buf.toString();
    }

    private static <T> List<T> copyAddresses(List<T> addresses) {
        List<T> copy = new ArrayList<T>(checkNotNull(addresses, "addresses"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("addresses must not be empty");
        }
        for (T address : copy) {
            checkNotNull(address, "address");
        }
        return Collections.unmodifiableList(copy);
    }

    /**
     * Returns the SvcParamKey.
     */
    public final int key() {
        return key;
    }

    /**
     * Encodes this parameter: its key, the length of its value and its value.
     */
    public final void encode(ByteBuf out) {
        out.writeShort(key);
        int lengthIndex = out.writerIndex();
        out.writeShort(0);
        encodeValue(out);
        int length = out.writerIndex() - lengthIndex - 2;
        if (length > 65535) {
            throw new IllegalArgumentException("SvcParamValue too long: " + length);
        }
        out.setShort(lengthIndex, length);
    }

    /**
     * Returns this parameter in the RFC 9460 presentation style, {@code key=value}. This implementation returns
     * {@code keyNNNNN}.
     */
    @Override
    public String toString() {
        return "key" + key;
    }

    /**
     * Encodes the value of this parameter. The default implementation writes an empty value.
     */
    protected void encodeValue(ByteBuf out) {
    }
}
