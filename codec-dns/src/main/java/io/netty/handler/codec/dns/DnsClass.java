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

import java.util.Arrays;

/**
 * Represents a class field in DNS protocol
 */
public class DnsClass {

    /**
     * Default class for DNS entries.
     */
    public static final DnsClass IN = new DnsClass(0x0001, "IN");
    public static final DnsClass CSNET = new DnsClass(0x0002, "CSNET");
    public static final DnsClass CHAOS = new DnsClass(0x0003, "CHAOS");
    public static final DnsClass HESIOD = new DnsClass(0x0004, "HESIOD");
    public static final DnsClass NONE = new DnsClass(0x00fe, "NONE");
    public static final DnsClass ANY = new DnsClass(0x00ff, "ANY");

    /**
     * The protocol value of this DNS class
     */
    private final int clazz;
    /**
     * The name of this DNS class
     */
    private final String name;

    DnsClass(int clazz, String name) {
        this.clazz = clazz;
        this.name = name;
    }

    /**
     * Returns the name of this class as used in bind config files
     */
    public final String name() {
        return name;
    }

    /**
     * Returns the protocol value represented by this class
     */
    public final int clazz() {
        return clazz;
    }

    /**
     * Returns an instance of DnsClass for a custom type.
     *
     * @param clazz The class
     * @param name The name
     */
    public static DnsClass create(int clazz, String name) {
        return new DnsClass(clazz, name);
    }

    /**
     * Returns true if this class is valid with respect to DNS protocol
     */
    public boolean isValid() {
        if (clazz < 1 || clazz > 4 && clazz != NONE.clazz && clazz != ANY.clazz) {
            return false;
        }
        return true;
    }

    public static DnsClass forName(String name) {
        if (IN.name.equals(name)) {
            return IN;
        } else if (NONE.name().equals(name)) {
            return NONE;
        } else if (ANY.name().equals(name)) {
            return ANY;
        } else if (CSNET.name().equals(name)) {
            return CSNET;
        } else if (CHAOS.name().equals(name)) {
            return CHAOS;
        } else if (HESIOD.name().equals(name)) {
            return HESIOD;
        }
        throw new IllegalArgumentException("name: " + name + " (expected: "
                + "IN, ANY, CSNET, CHAOS, HESIOD)");
    }

    public static DnsClass valueOf(int clazz) {
        switch (clazz) {
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
                throw new IllegalArgumentException("clazz: " + clazz + " (expected: "
                    + Arrays.asList(new Integer[] {IN.clazz, CSNET.clazz,
                    CHAOS.clazz, HESIOD.clazz, NONE.clazz, ANY.clazz}) + ")");
        }
    }

    @Override
    public final int hashCode() {
        return clazz;
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof DnsClass && ((DnsClass) o).clazz == clazz;
    }

    @Override
    public final String toString() {
        return name;
    }
}
