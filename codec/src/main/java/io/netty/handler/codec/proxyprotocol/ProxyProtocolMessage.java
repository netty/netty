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
public class ProxyProtocolMessage {

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
    public ProxyProtocolMessage() {
    }

    /**
     * Decode a version 2, binary proxy protocol header.
     *
     * @param header                   A version 2 proxy protocol header.
     * @return                         {@link ProxyProtocolMessage} instance.
     * @throws ProxyProtocolException  If any portion of the header is invalid.
     */
    public static final ProxyProtocolMessage decodeHeader(ByteBuf header) throws ProxyProtocolException {
        throw new ProxyProtocolException("version 2 headers are currently not supported");
    }

    /**
     * Decode a version 1, human-readable proxy protocol header.
     *
     * @param header                   A version 1 proxy protocol header.
     * @return                         {@link ProxyProtocolMessage} instance.
     * @throws ProxyProtocolException  If any portion of the header is invalid.
     */
    public static final ProxyProtocolMessage decodeHeader(String header) throws ProxyProtocolException {

        if (header == null) {
            throw new ProxyProtocolException("null header");
        }

        String[] parts = header.split(" ");
        int numParts = parts.length;

        if (numParts < 2) {
            throw new ProxyProtocolException(
                    "invalid format (header must at least contain protocol and proxied protocol values)"
            );
        }

        if (!"PROXY".equals(parts[0])) {
            throw new ProxyProtocolException("unsupported protocol " + parts[0]);
        }

        ProxiedProtocolAndFamily paf = ProxiedProtocolAndFamily.valueOf(parts[1]);

        if (paf == null) {
            throw new ProxyProtocolException("unsupported proxied protocol " + parts[1]);
        }

        // per spec, when the proxied protocol is 'UNKNOWN' we must discard all other values
        if (ProxiedProtocolAndFamily.UNKNOWN.equals(paf)) {
            ProxyProtocolMessage msg = new ProxyProtocolMessage();
            msg.setVersion(ProxyProtocolVersion.ONE);
            msg.setCommand(ProxyProtocolCommand.PROXY);
            msg.setProtocolAndFamily(paf);
            return msg;
        }

        if (numParts != 6) {
            throw new ProxyProtocolException(
                    "invalid format (header must contain exactly 6 values for TCP4 and TCP6 proxies)"
            );
        }

        ProxyProtocolMessage msg = new ProxyProtocolMessage();
        msg.setVersion(ProxyProtocolVersion.ONE);
        msg.setCommand(ProxyProtocolCommand.PROXY);
        msg.setProtocolAndFamily(paf);
        msg.setSourceAddress(parts[2]);
        msg.setDestinationAddress(parts[3]);
        msg.setSourcePort(parts[4]);
        msg.setDestinationPort(parts[5]);

        return msg;
    }

    /**
     * Validate an address (IPv4, IPv6, Unix Socket).
     *
     * @param address                  Human-readable address.
     * @throws ProxyProtocolException  If the address is invalid.
     */
    private void checkAddress(String address) throws ProxyProtocolException {

        ProxiedAddressFamily protoFamily = getProtocolAndFamily().getProxiedAddressFamily();

        if (protoFamily == null || ProxiedAddressFamily.UNSPECIFIED.equals(protoFamily)) {
            throw new ProxyProtocolException("unable to validate address because no protocol is set");
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
            throw new ProxyProtocolException(port + " is not a valid TCP port");
        }
    }

    /**
     * Set the UDP/TCP source port of this {@link ProxyProtocolMessage}.
     *
     * @return                         This {@link ProxyProtocolMessage}.
     * @throws ProxyProtocolException  If the port is not a valid integer or is out of range (0-65535 inclusive).
     */
    public ProxyProtocolMessage setSourcePort(String sourcePort) throws ProxyProtocolException {
        int srcPort;
        try {
            srcPort = Integer.parseInt(sourcePort);
        } catch (Exception e) {
            throw new ProxyProtocolException(sourcePort + " is not a valid port");
        }
        setSourcePort(srcPort);
        return this;
    }

    /**
     * Set the UDP/TCP destination port of this {@link ProxyProtocolMessage}.
     *
     * @return                         This {@link ProxyProtocolMessage}.
     * @throws ProxyProtocolException  If the port is not a valid integer or is out of range (0-65535 inclusive).
     */
    public ProxyProtocolMessage setDestinationPort(String destinationPort) throws ProxyProtocolException {
        int destPort;
        try {
            destPort = Integer.parseInt(destinationPort);
        } catch (Exception e) {
            throw new ProxyProtocolException(destinationPort + " is not a valid port", e);
        }
        setDestinationPort(destPort);
        return this;
    }

    /**
     * Returns the {@link ProxyProtocolVersion} of this {@link ProxyProtocolMessage}
     *
     * @return The proxy protocol specification version
     */
    public ProxyProtocolVersion getVersion() {
        return version;
    }

    /**
     * Set the {@link ProxyProtocolVersion} of this {@link ProxyProtocolMessage}.
     *
     * @return This {@link ProxyProtocolMessage}.
     */
    public ProxyProtocolMessage setVersion(ProxyProtocolVersion version) {
        this.version = version;
        return this;
    }

    /**
     * Returns the {@link ProxyProtocolCommand} of this {@link ProxyProtocolMessage}
     *
     * @return The proxy protocol command
     */
    public ProxyProtocolCommand getCommand() {
        return command;
    }

    /**
     * Set the {@link ProxyProtocolCommand} of this {@link ProxyProtocolMessage}.
     *
     * @return This {@link ProxyProtocolMessage}.
     */
    public ProxyProtocolMessage setCommand(ProxyProtocolCommand command) {
        this.command = command;
        return this;
    }

    /**
     * Returns the {@link ProxiedProtocolAndFamily} of this {@link ProxyProtocolMessage}
     *
     * @return The proxied protocol and address family
     */
    public ProxiedProtocolAndFamily getProtocolAndFamily() {
        return paf;
    }

    /**
     * Set the {@link ProxiedProtocolAndFamily} of this {@link ProxyProtocolMessage}.
     *
     * @return This {@link ProxyProtocolMessage}.
     */
    public ProxyProtocolMessage setProtocolAndFamily(ProxiedProtocolAndFamily paf) {
        this.paf = paf;
        return this;
    }

    /**
     * Returns the human-readable source address of this {@link ProxyProtocolMessage}
     *
     * @return The human-readable source address
     */
    public String getSourceAddress() {
        return sourceAddress;
    }

    /**
     * Set the human-readable source address of this {@link ProxyProtocolMessage}.
     *
     * @return                         This {@link ProxyProtocolMessage}.
     * @throws ProxyProtocolException  If the address is invalid.
     */
    public ProxyProtocolMessage setSourceAddress(String sourceAddress) throws ProxyProtocolException {
        checkAddress(sourceAddress);
        this.sourceAddress = sourceAddress;
        return this;
    }

    /**
     * Returns the human-readable destination address of this {@link ProxyProtocolMessage}
     *
     * @return The human-readable destination address
     */
    public String getDestinationAddress() {
        return destinationAddress;
    }

    /**
     * Set the human-readable destination address of this {@link ProxyProtocolMessage}.
     *
     * @return                         This {@link ProxyProtocolMessage}.
     * @throws ProxyProtocolException  If the address is invalid.
     */
    public ProxyProtocolMessage setDestinationAddress(String destinationAddress) throws ProxyProtocolException {
        checkAddress(destinationAddress);
        this.destinationAddress = destinationAddress;
        return this;
    }

    /**
     * Returns the UDP/TCP source port of this {@link ProxyProtocolMessage}
     *
     * @return The UDP/TCP source port
     */
    public int getSourcePort() {
        return sourcePort;
    }

    /**
     * Set the UDP/TCP source port of this {@link ProxyProtocolMessage}.
     *
     * @return                         This {@link ProxyProtocolMessage}.
     * @throws ProxyProtocolException  If the port is out of range (0-65535 inclusive).
     */
    public ProxyProtocolMessage setSourcePort(int sourcePort) throws ProxyProtocolException {
        checkPort(sourcePort);
        this.sourcePort = sourcePort;
        return this;
    }

    /**
     * Returns the UDP/TCP destination port of this {@link ProxyProtocolMessage}
     *
     * @return The UDP/TCP destination port
     */
    public int getDestinationPort() {
        return destinationPort;
    }

    /**
     * Set the UDP/TCP destination port of this {@link ProxyProtocolMessage}.
     *
     * @return                         This {@link ProxyProtocolMessage}.
     * @throws ProxyProtocolException  If the port is out of range (0-65535 inclusive).
     */
    public ProxyProtocolMessage setDestinationPort(int destinationPort) throws ProxyProtocolException {
        checkPort(destinationPort);
        this.destinationPort = destinationPort;
        return this;
    }

}
