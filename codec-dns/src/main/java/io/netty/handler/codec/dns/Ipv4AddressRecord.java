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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;
import java.net.Inet4Address;
import java.nio.charset.Charset;

/**
 * Represents an A record for an ipv4 address
 */
public final class Ipv4AddressRecord extends DnsEntry {

    private final int address;

    public Ipv4AddressRecord(String name, DnsType type, DnsClass dnsClass, long timeToLive,
            int[] addressParts) {
        this(name, type, dnsClass, timeToLive, pack(addressParts));
    }

    public Ipv4AddressRecord(String name, long timeToLive, Inet4Address address) {
        this(name, timeToLive, address.getHostAddress());
    }

    public Ipv4AddressRecord(String name, long timeToLive, String address) {
        this(name, DnsType.A, timeToLive, address);
    }

    public Ipv4AddressRecord(String name, DnsType type, long timeToLive, Inet4Address address) {
        this(name, type, DnsClass.IN, timeToLive, address.getHostAddress());
    }

    public Ipv4AddressRecord(String name, DnsType type, long timeToLive, String address) {
        this(name, type, DnsClass.IN, timeToLive, address);
    }

    public Ipv4AddressRecord(String name, DnsType type, DnsClass dnsClass, long timeToLive, Inet4Address address) {
        this(name, type, DnsClass.IN, timeToLive, address.getHostAddress());
    }

    public Ipv4AddressRecord(String name, DnsType type, DnsClass dnsClass, long timeToLive, String address) {
        this(name, type, dnsClass, timeToLive, parse(address));
    }

    public Ipv4AddressRecord(String name, long timeToLive, int address) {
        this(name, DnsType.A, timeToLive, address);
    }

    public Ipv4AddressRecord(String name, DnsType type, long timeToLive, int address) {
        this(name, type, DnsClass.IN, timeToLive, address);
    }

    public Ipv4AddressRecord(String name, DnsType type, DnsClass dnsClass, long timeToLive, int address) {
        super(name, type, dnsClass, timeToLive);
        this.address = address;
    }

    /**
     * Get the IP address as an integer.
     */
    public int address() {
        return address;
    }

    /**
     * Get a string representation of the address.
     */
    public String stringValue() {
        return addressToString(address());
    }

    /**
     * Get the address as an array of integers.
     */
    public int[] addressParts() {
        return toInts(address());
    }

    @Override
    protected void writePayload(NameWriter nameWriter, ByteBuf into, Charset charset) {
        into.writeInt(address());
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 97 * hash + this.address;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Ipv4AddressRecord other = (Ipv4AddressRecord) obj;
        if (this.address != other.address) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return new StringBuilder(128).append(StringUtil.simpleClassName(this))
                .append("(name: ").append(name())
                .append(", type: ").append(type())
                .append(", class: ").append(dnsClass())
                .append(", ttl: ").append(timeToLive())
                .append(", address: ").append(addressToString(address()))
                .append(')').toString();
    }

    static String addressToString(int addr) {
        StringBuilder sb = new StringBuilder();
        int[] ints = toInts(addr);
        for (int i = 0; i < ints.length; i++) {
            sb.append(ints[i]);
            if (i != ints.length - 1) {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    private static int[] toInts(int val) {
        int[] ret = new int[4];
        for (int j = 3; j >= 0; --j) {
            ret[j] |= (val >>> 8 * (3 - j)) & 0xff;
        }
        return ret;
    }

    private static int parse(String ipv4address) {
        String[] nums = ipv4address.split("\\.");
        if (nums.length != 4) {
            throw new IllegalArgumentException("Not an ipv4 address: " + ipv4address);
        }
        int[] result = new int[nums.length];
        for (int i = 0; i < nums.length; i++) {
            result[i] = Integer.parseInt(nums[i]);
        }
        return pack(result);
    }

    private static int pack(int... bytes) {
        int val = 0;
        for (int i = 0; i < bytes.length; i++) {
            val <<= 8;
            val |= bytes[i] & 0xff;
        }
        return val;
    }

    @Override
    public Ipv4AddressRecord withTimeToLive(long seconds) {
        return new Ipv4AddressRecord(name(), type(), dnsClass(), seconds, address);
    }
}
