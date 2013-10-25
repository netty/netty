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


import io.netty.util.internal.PlatformDependent;

import java.util.concurrent.ConcurrentMap;

/**
 * A special {@link Error} which is used to signal some state or request by throwing it.
 * {@link Signal} has an empty stack trace and has no cause to save the instantiation overhead.
 */
public final class Signal extends Error {

    private static final long serialVersionUID = -221145131122459977L;

    private static final ConcurrentMap<String, Boolean> map = PlatformDependent.newConcurrentHashMap();

    @SuppressWarnings("deprecation")
    private final UniqueName uname;

    /**
     * Creates a new {@link Signal} with the specified {@code name}.
     */
    @SuppressWarnings("deprecation")
    public static Signal valueOf(String name) {
        return new Signal(name);
    }

    /**
     * @deprecated Use {@link #valueOf(String)} instead.
     */
    @Deprecated
    public Signal(String name) {
        super(name);
        uname = new UniqueName(map, name);
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

    @Override
    public Throwable initCause(Throwable cause) {
        return this;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String toString() {
        return uname.name();
    }
}
