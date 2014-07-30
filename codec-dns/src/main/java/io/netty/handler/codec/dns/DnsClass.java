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

/**
 * Represents a class field in DNS protocol
 */
public final class DnsClass implements Comparable<DnsClass> {

    /**
     * Default class for DNS entries.
     */
    public static final DnsClass IN = new DnsClass(0x0001, "IN");
    public static final DnsClass CSNET = new DnsClass(0x0002, "CSNET");
    public static final DnsClass CHAOS = new DnsClass(0x0003, "CHAOS");
    public static final DnsClass HESIOD = new DnsClass(0x0004, "HESIOD");
    public static final DnsClass NONE = new DnsClass(0x00fe, "NONE");
    public static final DnsClass ANY = new DnsClass(0x00ff, "ANY");

    private static final String EXPECTED =
            " (expected: " +
            IN + '(' + IN.intValue() + "), " +
            CSNET + '(' + CSNET.intValue() + "), " +
            CHAOS + '(' + CHAOS.intValue() + "), " +
            HESIOD + '(' + HESIOD.intValue() + "), " +
            NONE + '(' + NONE.intValue() + "), " +
            ANY + '(' + ANY.intValue() + "))";

    public static DnsClass valueOf(String name) {
        if (IN.name().equals(name)) {
            return IN;
        }
        if (NONE.name().equals(name)) {
            return NONE;
        }
        if (ANY.name().equals(name)) {
            return ANY;
        }
        if (CSNET.name().equals(name)) {
            return CSNET;
        }
        if (CHAOS.name().equals(name)) {
            return CHAOS;
        }
        if (HESIOD.name().equals(name)) {
            return HESIOD;
        }

        throw new IllegalArgumentException("name: " + name + EXPECTED);
    }

    public static DnsClass valueOf(int intValue) {
        switch (intValue) {
        case 0x0001:
            return IN;
        case 0x0002:
            return CSNET;
        case 0x0003:
            return CHAOS;
        case 0x0004:
            return HESIOD;
        case 0x00fe:
            return NONE;
        case 0x00ff:
            return ANY;
        default:
            return new DnsClass(intValue, "UNKNOWN");
        }
    }

    /**
     * Returns an instance of DnsClass for a custom type.
     *
     * @param clazz The class
     * @param name The name
     */
    public static DnsClass valueOf(int clazz, String name) {
        return new DnsClass(clazz, name);
    }

    /**
     * The protocol value of this DNS class
     */
    private final int intValue;

    /**
     * The name of this DNS class
     */
    private final String name;

    private DnsClass(int intValue, String name) {
        if ((intValue & 0xffff) != intValue) {
            throw new IllegalArgumentException("intValue: " + intValue + " (expected: 0 ~ 65535)");
        }

        this.intValue = intValue;
        this.name = name;
    }

    /**
     * Returns the name of this class as used in bind config files
     */
    public String name() {
        return name;
    }

    /**
     * Returns the protocol value represented by this class
     */
    public int intValue() {
        return intValue;
    }

    @Override
    public int hashCode() {
        return intValue;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DnsClass && ((DnsClass) o).intValue == intValue;
    }

    @Override
    public int compareTo(DnsClass o) {
        return intValue() - o.intValue();
    }

    @Override
    public String toString() {
        return name;
    }
}
