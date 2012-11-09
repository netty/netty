/*
 * Copyright 2012 The Netty Project
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
package io.netty.monitor.spi;

import java.io.Serializable;

/**
 * <p>
 * Simple value class that identifies a monitoring/metrics provider like e.g. <a
 * href="">Yammer</a> by name.
 * </p>
 */
public final class MonitorProvider implements Serializable, Comparable<MonitorProvider> {

    private static final long serialVersionUID = -6549490566242173389L;

    /**
     * Create a new {@code MonitorProvider} instance having the supplied
     * {@code name}.
     * @param name The new {@code MonitorProvider}'s {@link #getName() name}.
     * @return A new {@code MonitorProvider} instance having the supplied
     *         {@code name}
     */
    public static MonitorProvider named(final String name) {
        return new MonitorProvider(name);
    }

    private final String name;

    private MonitorProvider(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (name.length() < 1) {
            throw new IllegalArgumentException("Argument 'name' must not be blank");
        }
        this.name = name;
    }

    /**
     * This {@code MonitorProvider}'s unique name.
     * @return This {@code MonitorProvider}'s unique name
     */
    public String getName() {
        return name;
    }

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final MonitorProvider o) {
        return name.compareTo(o.name);
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (name == null ? 0 : name.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MonitorProvider other = (MonitorProvider) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "MonitorProvider(" + name + ')';
    }
}
