/*
 * Copyright 2012 The Netty Project
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
package io.netty.util;


/**
 * A special {@link Error} which is used to signal some state or request by throwing it.
 * {@link Signal} has an empty stack trace and has no cause to save the instantiation overhead.
 */
public final class Signal extends Error implements Constant<Signal> {

    private static final long serialVersionUID = -221145131122459977L;

    private static final ConstantPool<Signal> pool = new ConstantPool<Signal>() {
        @Override
        protected Signal newConstant(int id, String name) {
            return new Signal(id, name);
        }
    };

    /**
     * Returns the {@link Signal} of the specified name.
     */
    public static Signal valueOf(String name) {
        return pool.valueOf(name);
    }

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    public static Signal valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return pool.valueOf(firstNameComponent, secondNameComponent);
    }

    private final SignalConstant constant;

    /**
     * Creates a new {@link Signal} with the specified {@code name}.
     */
    private Signal(int id, String name) {
        constant = new SignalConstant(id, name);
    }

    /**
     * Check if the given {@link Signal} is the same as this instance. If not an {@link IllegalStateException} will
     * be thrown.
     */
    public void expect(Signal signal) {
        if (this != signal) {
            throw new IllegalStateException("unexpected signal: " + signal);
        }
    }

    // Suppress a warning since the method doesn't need synchronization
    @Override
    public Throwable initCause(Throwable cause) {   // lgtm[java/non-sync-override]
        return this;
    }

    // Suppress a warning since the method doesn't need synchronization
    @Override
    public Throwable fillInStackTrace() {   // lgtm[java/non-sync-override]
        return this;
    }

    @Override
    public int id() {
        return constant.id();
    }

    @Override
    public String name() {
        return constant.name();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public int compareTo(Signal other) {
        if (this == other) {
            return 0;
        }

        return constant.compareTo(other.constant);
    }

    @Override
    public String toString() {
        return name();
    }

    private static final class SignalConstant extends AbstractConstant<SignalConstant> {
        SignalConstant(int id, String name) {
            super(id, name);
        }
    }
}
