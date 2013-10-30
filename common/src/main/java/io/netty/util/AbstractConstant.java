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
package io.netty.util;

/**
 * Base implementation of {@link Constant}.
 */
public abstract class AbstractConstant<T extends AbstractConstant<T>> implements Constant<T> {

    private final int id;
    private final String name;

    /**
     * Creates a new instance.
     */
    protected AbstractConstant(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final int id() {
        return id;
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public final int compareTo(T other) {
        if (this == other) {
            return 0;
        }

        int returnCode = name.compareTo(other.name());
        if (returnCode != 0) {
            return returnCode;
        }

        return ((Integer) id).compareTo(other.id());
    }

    @Override
    public final String toString() {
        return name();
    }
}
