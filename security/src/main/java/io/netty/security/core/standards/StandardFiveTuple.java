/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core.standards;

import io.netty.security.core.Address;
import io.netty.security.core.FiveTuple;
import io.netty.security.core.Protocol;
import io.netty.util.internal.ObjectUtil;

import static io.netty.security.core.Util.compareIntegers;
import static io.netty.security.core.Util.hash;

public final class StandardFiveTuple implements FiveTuple {
    private final Protocol protocol;
    private final int sourcePort;
    private final int destinationPort;
    private final Address sourceIpAddress;
    private final Address destinationIpAddress;

    private StandardFiveTuple(Protocol protocol, int sourcePort, int destinationPort,
                              Address sourceIpAddress, Address destinationIpAddress) {
        this.protocol = ObjectUtil.checkNotNull(protocol, "Protocol");
        this.sourcePort = ObjectUtil.checkInRange(sourcePort, 1, 65535, "SourcePort");
        this.destinationPort = ObjectUtil.checkInRange(destinationPort, 1, 65535, "DestinationPort");
        this.sourceIpAddress = ObjectUtil.checkNotNull(sourceIpAddress, "SourceIPAddress");
        this.destinationIpAddress = ObjectUtil.checkNotNull(destinationIpAddress, "DestinationIPAddress");
    }

    /**
     * Create a new {@link StandardFiveTuple}
     *
     * @param protocol             {@link Protocol} type
     * @param sourcePort           Source Port
     * @param destinationPort      Destination Port
     * @param sourceIpAddress      Source IP Address
     * @param destinationIpAddress Destination IP Address
     * @return new {@link StandardFiveTuple} instance
     */
    public static StandardFiveTuple from(Protocol protocol, int sourcePort, int destinationPort,
                                         Address sourceIpAddress, Address destinationIpAddress) {
        return new StandardFiveTuple(protocol, sourcePort, destinationPort, sourceIpAddress, destinationIpAddress);
    }

    @Override
    public Protocol protocol() {
        return protocol;
    }

    @Override
    public int sourcePort() {
        return sourcePort;
    }

    @Override
    public int destinationPort() {
        return destinationPort;
    }

    @Override
    public Address sourceIpAddress() {
        return sourceIpAddress;
    }

    @Override
    public Address destinationIpAddress() {
        return destinationIpAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StandardFiveTuple that = (StandardFiveTuple) o;
        return hashCode() == that.hashCode();
    }

    @Override
    public int compareTo(FiveTuple o) {
        // Protocol
        int compare = compareIntegers(protocol().ordinal(), o.protocol().ordinal());
        if (compare != 0) {
            return compare;
        }

        // Source Port
        compare = compareIntegers(sourcePort(), o.sourcePort());
        if (compare != 0) {
            return compare;
        }

        // Destination Port
        compare = compareIntegers(destinationPort(), o.destinationPort());
        if (compare != 0) {
            return compare;
        }

        // If Source version is v4 then we have v4 destination as well.
        // If Source version is v6 then we have v6 destination as well.
        if (sourceIpAddress().version() == Address.Version.v4) {
            // Source Address
            compare = compareIntegers(sourceIpAddress().v4AddressAsInt(), o.sourceIpAddress().v4AddressAsInt());
            if (compare != 0) {
                return compare;
            }

            return compareIntegers(destinationIpAddress().v4AddressAsInt(), o.destinationIpAddress().v4AddressAsInt());
        }
        if (sourceIpAddress().version() == Address.Version.v6) {
            compare = sourceIpAddress().v6NetworkAddressAsBigInt()
                    .compareTo(o.sourceIpAddress().v6NetworkAddressAsBigInt());
            if (compare != 0) {
                return compare;
            }

            return destinationIpAddress().v6NetworkAddressAsBigInt()
                    .compareTo(o.destinationIpAddress().v6NetworkAddressAsBigInt());
        }

        // Unsupported IP version. So let's return -1
        // because we have no idea at all about it.
        return -1;
    }

    @Override
    public int hashCode() {
        return hash(protocol, sourcePort, destinationPort, sourceIpAddress, destinationIpAddress);
    }

    @Override
    public String toString() {
        return "StandardFiveTuple{" +
                "protocol=" + protocol +
                ", sourcePort=" + sourcePort +
                ", destinationPort=" + destinationPort +
                ", sourceIpAddress=" + sourceIpAddress +
                ", destinationIpAddress=" + destinationIpAddress +
                '}';
    }
}
