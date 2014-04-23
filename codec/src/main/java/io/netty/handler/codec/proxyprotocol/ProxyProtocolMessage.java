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
package io.netty.handler.codec.proxyprotocol;

import io.netty.buffer.ByteBuf;
import io.netty.util.NetUtil;

/**
 * Message container for decoded proxy protocol parameters
 */
public final class ProxyProtocolMessage {
    /**
     * Version 1 proxy protocol message for 'UNKNOWN' proxied protocols. Per spec, when the proxied protocol is
     * 'UNKNOWN' we must discard all other header values.
     */
    private static final ProxyProtocolMessage V1_UNKNOWN_MSG = new ProxyProtocolMessage(
            ProxyProtocolVersion.ONE, ProxyProtocolCommand.PROXY, ProxiedProtocolAndFamily.UNKNOWN, null, null, 0, 0);

    private ProxyProtocolVersion version;
    private ProxyProtocolCommand command;
    private ProxiedProtocolAndFamily paf;
    private String sourceAddress;
    private String destinationAddress;
    private int sourcePort;
    private int destinationPort;

    /**
     * Creates a new instance.
     */
    private ProxyProtocolMessage(ProxyProtocolVersion ver, ProxyProtocolCommand cmd, ProxiedProtocolAndFamily paf,
                                 String srcAddress, String dstAddress, String srcPort, String dstPort) {
        this(ver, cmd, paf, srcAddress, dstAddress, portStringToInt(srcPort), portStringToInt(dstPort));
    }

    /**
     * Creates a new instance.
     */
    private ProxyProtocolMessage(ProxyProtocolVersion ver, ProxyProtocolCommand cmd, ProxiedProtocolAndFamily paf,
                                 String srcAddress, String dstAddress, int srcPort, int dstPort) {
        this.version = ver;
        this.command = cmd;
        this.paf = paf;

        checkAddress(srcAddress);
        checkAddress(dstAddress);
        checkPort(srcPort);
        checkPort(dstPort);

        this.sourceAddress = srcAddress;
        this.destinationAddress = dstAddress;
        this.sourcePort = srcPort;
        this.destinationPort = dstPort;
    }

    /**
     * Decode a version 2, binary proxy protocol header.
     *
     * @param header                   A version 2 proxy protocol header.
     * @return                         {@link ProxyProtocolMessage} instance.
     * @throws ProxyProtocolException  If any portion of the header is invalid.
     */
    public static ProxyProtocolMessage decodeHeader(ByteBuf header) throws ProxyProtocolException {
        throw new ProxyProtocolException("version 2 headers are currently not supported");
    }

    /**
     * Decode a version 1, human-readable proxy protocol header.
     *
     * @param header                   A version 1 proxy protocol header.
     * @return                         {@link ProxyProtocolMessage} instance.
     * @throws ProxyProtocolException  If any portion of the header is invalid.
     */
    public static ProxyProtocolMessage decodeHeader(String header) throws ProxyProtocolException {

        if (header == null) {
            throw new ProxyProtocolException("null header");
        }

        String[] parts = header.split(" ");
        int numParts = parts.length;

        if (numParts < 2) {
            throw new ProxyProtocolException(
                    "invalid format (header must at least contain protocol and proxied protocol values)");
        }

        if (!"PROXY".equals(parts[0])) {
            throw new ProxyProtocolException("unsupported protocol " + parts[0]);
        }

        ProxiedProtocolAndFamily protAndFam = ProxiedProtocolAndFamily.valueOf(parts[1]);

        boolean validPaf = protAndFam != null &&
                (ProxiedProtocolAndFamily.TCP4.equals(protAndFam) || ProxiedProtocolAndFamily.TCP6.equals(protAndFam) ||
                ProxiedProtocolAndFamily.UNKNOWN.equals(protAndFam));

        if (!validPaf) {
            throw new ProxyProtocolException("unsupported v1 proxied protocol " + parts[1]);
        }

        if (ProxiedProtocolAndFamily.UNKNOWN.equals(protAndFam)) {
            return V1_UNKNOWN_MSG;
        }

        if (numParts != 6) {
            throw new ProxyProtocolException("invalid format (header must contain exactly 6 values for TCP proxies)");
        }

        return new ProxyProtocolMessage(ProxyProtocolVersion.ONE, ProxyProtocolCommand.PROXY,
                protAndFam, parts[2], parts[3], parts[4], parts[5]);
    }

    /**
     * Convert port to integer.
     *
     * @param port                     The port
     * @return                         Port as integer
     * @throws ProxyProtocolException  If port is not a valid integer
     */
    private static int portStringToInt(String port) throws ProxyProtocolException {
        try {
            return Integer.parseInt(port);
        } catch (Exception e) {
            throw new ProxyProtocolException(port + " is not a valid port", e);
        }
    }

    /**
     * Validate an address (IPv4, IPv6, Unix Socket).
     *
     * @param address                  Human-readable address.
     * @throws ProxyProtocolException  If the address is invalid.
     */
    private void checkAddress(String address) throws ProxyProtocolException {

        ProxiedAddressFamily protoFamily = protocolAndFamily().proxiedAddressFamily();

        if (protoFamily == null) {
            throw new ProxyProtocolException("unable to validate address because no protocol is set");
        }

        if (ProxiedAddressFamily.UNSPECIFIED.equals(protoFamily) && address != null) {
            throw new ProxyProtocolException(
                    "unable to validate address because protocol is " + protoFamily.toString());
        }

        if (ProxiedAddressFamily.UNIX.equals(protoFamily)) {
            return;
        }

        boolean isValid = true;

        if (ProxiedAddressFamily.IPV4.equals(protoFamily)) {
            isValid = NetUtil.isValidIpV4Address(address);
        } else if (ProxiedAddressFamily.IPV6.equals(protoFamily)) {
            isValid = NetUtil.isValidIpV6Address(address);
        }

        if (!isValid) {
            throw new ProxyProtocolException(address + " is not a valid " + protoFamily.toString() + " address");
        }
    }

    /**
     * Validate a UDP/TCP port.
     *
     * @param port                     UDP/TCP port.
     * @throws ProxyProtocolException  If the port is out of range (0-65535 inclusive).
     */
    private void checkPort(int port) throws ProxyProtocolException {
        if (port < 0 || port > 65535) {
            throw new ProxyProtocolException(port + " is not a valid port");
        }
    }

    /**
     * Returns the {@link ProxyProtocolVersion} of this {@link ProxyProtocolMessage}
     *
     * @return The proxy protocol specification version
     */
    public ProxyProtocolVersion version() {
        return version;
    }

    /**
     * Returns the {@link ProxyProtocolCommand} of this {@link ProxyProtocolMessage}
     *
     * @return The proxy protocol command
     */
    public ProxyProtocolCommand command() {
        return command;
    }

    /**
     * Returns the {@link ProxiedProtocolAndFamily} of this {@link ProxyProtocolMessage}
     *
     * @return The proxied protocol and address family
     */
    public ProxiedProtocolAndFamily protocolAndFamily() {
        return paf;
    }

    /**
     * Returns the human-readable source address of this {@link ProxyProtocolMessage}
     *
     * @return The human-readable source address
     */
    public String sourceAddress() {
        return sourceAddress;
    }

    /**
     * Returns the human-readable destination address of this {@link ProxyProtocolMessage}
     *
     * @return The human-readable destination address
     */
    public String destinationAddress() {
        return destinationAddress;
    }

    /**
     * Returns the UDP/TCP source port of this {@link ProxyProtocolMessage}
     *
     * @return The UDP/TCP source port
     */
    public int sourcePort() {
        return sourcePort;
    }

    /**
     * Returns the UDP/TCP destination port of this {@link ProxyProtocolMessage}
     *
     * @return The UDP/TCP destination port
     */
    public int destinationPort() {
        return destinationPort;
    }
}
