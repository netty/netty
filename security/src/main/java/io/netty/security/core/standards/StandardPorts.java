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

import io.netty.security.core.Ports;
import io.netty.util.internal.ObjectUtil;

import static io.netty.security.core.Util.compareIntegers;
import static io.netty.security.core.Util.hash;

public final class StandardPorts implements Ports {

    private final int start;
    private final int end;

    private StandardPorts(int start, int end) {
        this.start = ObjectUtil.checkInRange(start, 1, 65535, "PortStart");
        this.end = ObjectUtil.checkInRange(end, 1, 65535, "PortEnd");

        if (start > end) {
            throw new IllegalArgumentException("Port start must be smaller than Port end");
        }
    }

    /**
     * Create a new {@link StandardPorts} instance with specified start
     * and end port.
     *
     * @param start Port Start
     * @param end   Port End
     * @return {@link StandardPorts} instance
     */
    public static StandardPorts from(int start, int end) {
        return new StandardPorts(start, end);
    }

    /**
     * Create a new {@link StandardPorts} instance with specified start.
     *
     * @param port Port
     * @return {@link StandardPorts} instance
     */
    public static StandardPorts of(int port) {
        return new StandardPorts(port, port);
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public int end() {
        return end;
    }

    @Override
    public int compareTo(Ports ports) {
        return compare(ports.start(), ports.end());
    }

    @Override
    public int lookup(int port) {
        return compare(port, port);
    }

    @Override
    public boolean lookupPort(int port) {
        return compare(port, port) == 0;
    }

    public int compare(int start, int end) {
        if (start >= start() && end <= end()) {
            return 0;
        }

        int compare = compareIntegers(start, start());
        if (compare != 0) {
            return compare;
        }

        return compareIntegers(end, end());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StandardPorts that = (StandardPorts) o;
        return hashCode() == that.hashCode();
    }

    @Override
    public int hashCode() {
        return hash(start, end);
    }

    @Override
    public String toString() {
        return "StandardPorts{start=" + start + ", end=" + end + '}';
    }
}
