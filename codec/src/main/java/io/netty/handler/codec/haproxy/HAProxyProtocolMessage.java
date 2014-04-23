/*
 * Copyright 2014 The Netty Project
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
import io.netty.buffer.ByteBufProcessor;
import io.netty.util.internal.StringUtil;
import io.netty.util.NetUtil;

import java.nio.charset.Charset;

/**
 * Message container for decoded HAProxy proxy protocol parameters
 */
public final class HAProxyProtocolMessage {
    /**
     * The default system character encoding
     */
    private static final Charset SYSTEM_CHARSET = Charset.defaultCharset();

    /**
     * Version 1 proxy protocol message for 'UNKNOWN' proxied protocols. Per spec, when the proxied protocol is
     * 'UNKNOWN' we must discard all other header values.
     */
    private static final HAProxyProtocolMessage V1_UNKNOWN_MSG = new HAProxyProtocolMessage(HAProxyProtocolVersion.ONE,
            HAProxyProtocolCommand.PROXY, ProxiedProtocolAndFamily.UNKNOWN, null, null, 0, 0);

    /**
     * Version 2 proxy protocol message for 'UNKNOWN' proxied protocols. Per spec, when the proxied protocol is
     * 'UNKNOWN' we must discard all other header values.
     */
    private static final HAProxyProtocolMessage V2_UNKNOWN_MSG = new HAProxyProtocolMessage(HAProxyProtocolVersion.TWO,
        HAProxyProtocolCommand.PROXY, ProxiedProtocolAndFamily.UNKNOWN, null, null, 0, 0);

    /**
     * Version 2 proxy protocol message for local requests. Per spec, we should use an unspecified protocol and family
     * for 'LOCAL' commands. Per spec, when the proxied protocol is 'UNKNOWN' we must discard all other header values.
     */
    private static final HAProxyProtocolMessage V2_LOCAL_MSG = new HAProxyProtocolMessage(HAProxyProtocolVersion.TWO,
        HAProxyProtocolCommand.LOCAL, ProxiedProtocolAndFamily.UNKNOWN, null, null, 0, 0);

    private final HAProxyProtocolVersion version;
    private final HAProxyProtocolCommand command;
    private final ProxiedProtocolAndFamily paf;
    private final String sourceAddress;
    private final String destinationAddress;
    private final int sourcePort;
    private final int destinationPort;

    /**
     * Creates a new instance
     */
    private HAProxyProtocolMessage(HAProxyProtocolVersion ver, HAProxyProtocolCommand cmd, ProxiedProtocolAndFamily paf,
                                   String srcAddress, String dstAddress, String srcPort, String dstPort) {
        this(ver, cmd, paf, srcAddress, dstAddress, portStringToInt(srcPort), portStringToInt(dstPort));
    }

    /**
     * Creates a new instance
     */
    private HAProxyProtocolMessage(HAProxyProtocolVersion ver, HAProxyProtocolCommand cmd, ProxiedProtocolAndFamily paf,
                                   String srcAddress, String dstAddress, int srcPort, int dstPort) {

        ProxiedAddressFamily addrFamily;
        if (paf != null) {
            addrFamily = paf.proxiedAddressFamily();
        } else {
            addrFamily = null;
        }

        checkAddress(srcAddress, addrFamily);
        checkAddress(dstAddress, addrFamily);
        checkPort(srcPort);
        checkPort(dstPort);

        this.version = ver;
        this.command = cmd;
        this.paf = paf;
        this.sourceAddress = srcAddress;
        this.destinationAddress = dstAddress;
        this.sourcePort = srcPort;
        this.destinationPort = dstPort;
    }

    /**
     * Decode a version 2, binary proxy protocol header
     *
     * @param header                     a version 2 proxy protocol header
     * @return                           {@link HAProxyProtocolMessage} instance
     * @throws HAProxyProtocolException  if any portion of the header is invalid
     */
    static HAProxyProtocolMessage decodeHeader(ByteBuf header) throws HAProxyProtocolException {
        if (header == null) {
            throw new HAProxyProtocolException("null header");
        }

        if (header.readableBytes() < 16) {
            throw new HAProxyProtocolException("incomplete header (header must be at least 16 bytes)");
        }

        // Per spec, the 13th byte is the protocol version and command byte
        header.skipBytes(12);
        final byte verCmdByte = header.readByte();

        HAProxyProtocolVersion ver = HAProxyProtocolVersion.valueOf(verCmdByte);

        if (ver == null || !HAProxyProtocolVersion.TWO.equals(ver)) {
            throw new HAProxyProtocolException("unsupported header version 0x" + Integer.toHexString(verCmdByte));
        }

        HAProxyProtocolCommand cmd = HAProxyProtocolCommand.valueOf(verCmdByte);

        if (cmd == null) {
            throw new HAProxyProtocolException("unkown command 0x" + Integer.toHexString(verCmdByte));
        }

        if (HAProxyProtocolCommand.LOCAL.equals(cmd)) {
            return V2_LOCAL_MSG;
        }

        // Per spec, the 14th byte is the protocol and address family byte
        ProxiedProtocolAndFamily protAndFam = ProxiedProtocolAndFamily.valueOf(header.readByte());

        if (protAndFam == null) {
            throw new HAProxyProtocolException("unkown protocol and family");
        }

        if (ProxiedProtocolAndFamily.UNKNOWN.equals(protAndFam)) {
            return V2_UNKNOWN_MSG;
        }

        int addressInfoLen = header.readUnsignedShort();

        String srcAddress;
        String dstAddress;
        int addressLen;
        int srcPort = 0;
        int dstPort = 0;

        ProxiedAddressFamily addressFamily = protAndFam.proxiedAddressFamily();

        if (ProxiedAddressFamily.UNIX.equals(addressFamily)) {
            // unix sockets require 216 bytes for address information
            if (addressInfoLen < 216 || header.readableBytes() < 216) {
                throw new HAProxyProtocolException(
                    "incomplete address information (unix socket address info must be at least 216 bytes)");
            }
            int startIdx = header.readerIndex();
            int addressEnd = header.forEachByte(startIdx, 108, ByteBufProcessor.FIND_NUL);
            if (addressEnd == -1) {
                addressLen = 108;
            } else {
                addressLen = addressEnd - startIdx;
            }
            srcAddress = header.toString(startIdx, addressLen, SYSTEM_CHARSET);

            startIdx = startIdx + 108;

            addressEnd = header.forEachByte(startIdx, 108, ByteBufProcessor.FIND_NUL);
            if (addressEnd == -1) {
                addressLen = 108;
            } else {
                addressLen = addressEnd - startIdx;
            }
            dstAddress = header.toString(startIdx, addressLen, SYSTEM_CHARSET);
        } else {
            if (ProxiedAddressFamily.IPV4.equals(addressFamily)) {
                // IPv4 requires 12 bytes for address information
                if (addressInfoLen < 12 || header.readableBytes() < 12) {
                    throw new HAProxyProtocolException(
                        "incomplete address information (IPv4 address info must be at least 12 bytes)");
                }
                addressLen = 4;
            } else if (ProxiedAddressFamily.IPV6.equals(addressFamily)) {
                // IPv6 requires 36 bytes for address information
                if (addressInfoLen < 36 || header.readableBytes() < 36) {
                    throw new HAProxyProtocolException(
                        "incomplete address information (IPv6 address info must be at least 36 bytes)");
                }
                addressLen = 16;
            } else {
                throw new HAProxyProtocolException(
                    "unable to parse address information (unkown address family " + addressFamily + ")");
            }

            // Per spec, the src address begins at the 17th byte
            srcAddress = ipBytestoString(header, addressLen);
            dstAddress = ipBytestoString(header, addressLen);
            srcPort = header.readUnsignedShort();
            dstPort = header.readUnsignedShort();
        }

        return new HAProxyProtocolMessage(ver, cmd, protAndFam, srcAddress, dstAddress, srcPort, dstPort);
    }

    /**
     * Decode a version 1, human-readable proxy protocol header
     *
     * @param header                     a version 1 proxy protocol header
     * @return                           {@link HAProxyProtocolMessage} instance
     * @throws HAProxyProtocolException  if any portion of the header is invalid
     */
    static HAProxyProtocolMessage decodeHeader(String header) throws HAProxyProtocolException {
        if (header == null) {
            throw new HAProxyProtocolException("null header");
        }

        String[] parts = StringUtil.split(header, ' ');
        int numParts = parts.length;

        if (numParts < 2) {
            throw new HAProxyProtocolException(
                    "invalid format (header must at least contain protocol and proxied protocol values)");
        }

        if (!"PROXY".equals(parts[0])) {
            throw new HAProxyProtocolException("unsupported protocol " + parts[0]);
        }

        ProxiedProtocolAndFamily protAndFam = ProxiedProtocolAndFamily.valueOf(parts[1]);

        boolean validPaf = protAndFam != null &&
                (ProxiedProtocolAndFamily.TCP4.equals(protAndFam) || ProxiedProtocolAndFamily.TCP6.equals(protAndFam) ||
                ProxiedProtocolAndFamily.UNKNOWN.equals(protAndFam));

        if (!validPaf) {
            throw new HAProxyProtocolException("unsupported v1 proxied protocol " + parts[1]);
        }

        if (ProxiedProtocolAndFamily.UNKNOWN.equals(protAndFam)) {
            return V1_UNKNOWN_MSG;
        }

        if (numParts != 6) {
            throw new HAProxyProtocolException("invalid format (header must contain exactly 6 values for TCP proxies)");
        }

        return new HAProxyProtocolMessage(HAProxyProtocolVersion.ONE, HAProxyProtocolCommand.PROXY,
                protAndFam, parts[2], parts[3], parts[4], parts[5]);
    }

    /**
     * Convert ip address bytes to string representation
     *
     * @param header     buffer containing ip address bytes
     * @param addressLen number of bytes to read (4 bytes for IPv4, 16 bytes for IPv6)
     * @return           string representation of the ip address
     */
    private static String ipBytestoString(ByteBuf header, int addressLen) {
        StringBuilder sb = new StringBuilder();
        if (addressLen == 4) {
            sb.append(header.readByte() & 0xff);
            sb.append(".");
            sb.append(header.readByte() & 0xff);
            sb.append(".");
            sb.append(header.readByte() & 0xff);
            sb.append(".");
            sb.append(header.readByte() & 0xff);
        } else {
            sb.append(Integer.toHexString(header.readUnsignedShort()));
            sb.append(":");
            sb.append(Integer.toHexString(header.readUnsignedShort()));
            sb.append(":");
            sb.append(Integer.toHexString(header.readUnsignedShort()));
            sb.append(":");
            sb.append(Integer.toHexString(header.readUnsignedShort()));
            sb.append(":");
            sb.append(Integer.toHexString(header.readUnsignedShort()));
            sb.append(":");
            sb.append(Integer.toHexString(header.readUnsignedShort()));
            sb.append(":");
            sb.append(Integer.toHexString(header.readUnsignedShort()));
            sb.append(":");
            sb.append(Integer.toHexString(header.readUnsignedShort()));
        }
        return sb.toString();
    }

    /**
     * Convert port to integer
     *
     * @param port                       the port
     * @return                           port as integer
     * @throws HAProxyProtocolException  if port is not a valid integer
     */
    private static int portStringToInt(String port) throws HAProxyProtocolException {
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException e) {
            throw new HAProxyProtocolException(port + " is not a valid port", e);
        }
    }

    /**
     * Validate an address (IPv4, IPv6, Unix Socket)
     *
     * @param address                    human-readable address
     * @param addrFamily                 the {@link ProxiedAddressFamily} to check the address against
     * @throws HAProxyProtocolException  if the address is invalid
     */
    private static void checkAddress(String address, ProxiedAddressFamily addrFamily) throws HAProxyProtocolException {
        if (addrFamily == null) {
            throw new HAProxyProtocolException("unable to validate address because no address family is set");
        }

        if (ProxiedAddressFamily.UNSPECIFIED.equals(addrFamily) && address != null) {
            throw new HAProxyProtocolException(
                    "unable to validate address because address family is " + addrFamily);
        }

        if (ProxiedAddressFamily.UNIX.equals(addrFamily)) {
            return;
        }

        boolean isValid = true;

        if (ProxiedAddressFamily.IPV4.equals(addrFamily)) {
            isValid = NetUtil.isValidIpV4Address(address);
        } else if (ProxiedAddressFamily.IPV6.equals(addrFamily)) {
            isValid = NetUtil.isValidIpV6Address(address);
        }

        if (!isValid) {
            throw new HAProxyProtocolException(address + " is not a valid " + addrFamily + " address");
        }
    }

    /**
     * Validate a UDP/TCP port
     *
     * @param port                       the UDP/TCP port
     * @throws HAProxyProtocolException  if the port is out of range (0-65535 inclusive)
     */
    private static void checkPort(int port) throws HAProxyProtocolException {
        if (port < 0 || port > 65535) {
            throw new HAProxyProtocolException(port + " is not a valid port");
        }
    }

    /**
     * Returns the {@link HAProxyProtocolVersion} of this {@link HAProxyProtocolMessage}
     *
     * @return the proxy protocol specification version
     */
    public HAProxyProtocolVersion version() {
        return version;
    }

    /**
     * Returns the {@link HAProxyProtocolCommand} of this {@link HAProxyProtocolMessage}
     *
     * @return the proxy protocol command
     */
    public HAProxyProtocolCommand command() {
        return command;
    }

    /**
     * Returns the {@link ProxiedProtocolAndFamily} of this {@link HAProxyProtocolMessage}
     *
     * @return the proxied protocol and address family
     */
    public ProxiedProtocolAndFamily protocolAndFamily() {
        return paf;
    }

    /**
     * Returns the human-readable source address of this {@link HAProxyProtocolMessage}
     *
     * @return the human-readable source address
     */
    public String sourceAddress() {
        return sourceAddress;
    }

    /**
     * Returns the human-readable destination address of this {@link HAProxyProtocolMessage}
     *
     * @return the human-readable destination address
     */
    public String destinationAddress() {
        return destinationAddress;
    }

    /**
     * Returns the UDP/TCP source port of this {@link HAProxyProtocolMessage}
     *
     * @return the UDP/TCP source port
     */
    public int sourcePort() {
        return sourcePort;
    }

    /**
     * Returns the UDP/TCP destination port of this {@link HAProxyProtocolMessage}
     *
     * @return the UDP/TCP destination port
     */
    public int destinationPort() {
        return destinationPort;
    }
}
