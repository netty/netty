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
package io.netty.security.core;

import java.math.BigInteger;
import java.net.InetAddress;

/**
 * Address is a context holder which holds {@link InetAddress}, IPv4 Address as {@link Integer},
 * IPv4 Address Subnet Mask as {@link Integer}, IPv6 Address as {@link BigInteger},
 * IPv6 Address Subnet Mask as {@link BigInteger}, and {@link Version} of IP address.
 */
public interface Address extends Comparable<Address> {

    /**
     * Accept any Address
     */
    Address ANY_ADDRESS = new Address() {
        @Override
        public InetAddress address() {
            return null;
        }

        @Override
        public int v4AddressAsInt() {
            return 0;
        }

        @Override
        public int v4SubnetMaskAsInt() {
            return 0;
        }

        @Override
        public BigInteger v6NetworkAddressAsBigInt() {
            return null;
        }

        @Override
        public BigInteger v6SubnetMaskAsBigInt() {
            return null;
        }

        @Override
        public Version version() {
            return Version.v4;
        }

        @Override
        public int compareTo(Address address) {
            return 0;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return getClass() == o.getClass();
        }
    };

    /**
     * Return {@link InetAddress} of this Address
     */
    InetAddress address();

    /**
     * Return {@link Integer} address of this Address
     * <p>
     * Call {@link #version()} to determine if this address is present or not
     */
    int v4AddressAsInt();

    /**
     * Subnet mask of this Address
     * <p>
     * Call {@link #version()} to determine if this address is present or not
     */
    int v4SubnetMaskAsInt();

    /**
     * Return {@link BigInteger} address of this Address
     * <p>
     * Call {@link #version()} to determine if this address is present or not
     */
    BigInteger v6NetworkAddressAsBigInt();

    /**
     * Subnet mask of this Address
     * <p>
     * Call {@link #version()} to determine if this address is present or not
     */
    BigInteger v6SubnetMaskAsBigInt();

    /**
     * Return {@link Version} of this Address
     */
    Version version();

    /**
     * Try to match an {@link Address} with this {@link IpAddress}
     *
     * @param address {@link Address} to match
     * @return {@code true} if match was successful else {@code false}
     */
    @Override
    int compareTo(Address address);

    /**
     * IP address version
     */
    enum Version {

        /**
         * IPv4 address
         */
        v4,

        /**
         * IPv6 address
         */
        v6
    }
}
