/*
 * Copyright 2013 The Netty Project
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
 * A class representing entries in a DNS packet (questions, and all resource
 * records). Contains data shared by entries such as name, type, and class.
 */
public class DnsEntry {

    private final String name;
    private final DnsType type;
    private final DnsClass dnsClass;

    // only allow to extend from same package
    DnsEntry(String name, DnsType type, DnsClass dnsClass) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (!dnsClass.isValid()) {
            throw new IllegalArgumentException("an invalid class has been supplied.");
        }
        this.name = name;
        this.type = type;
        this.dnsClass = dnsClass;
    }

    /**
     * Returns the name of this entry (the domain).
     */
    public String name() {
        return name;
    }

    /**
     * Returns the type of resource record to be received.
     */
    public DnsType type() {
        return type;
    }

    /**
     * Returns the class for this entry. Default is IN (Internet).
     */
    public DnsClass dnsClass() {
        return dnsClass;
    }

    @Override
    public int hashCode() {
        return (name.hashCode() * 31 + type.hashCode()) * 31 + dnsClass.hashCode();
    }

    @Override
    public String toString() {
        return new StringBuilder(32).append(getClass().getSimpleName()).append("(domain name: ").append(name)
                .append(", type: ").append(type).append(", class: ").append(dnsClass).append(')').toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof DnsEntry) {
            DnsEntry other = (DnsEntry) o;
            return other.type() == type && other.dnsClass() == dnsClass && other.name().equals(name);
        }
        return false;
    }
}
