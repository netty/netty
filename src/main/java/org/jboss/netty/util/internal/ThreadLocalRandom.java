/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package org.jboss.netty.util.internal;

import java.util.Random;

/**
 * A random number generator isolated to the current thread.  Like the
 * global {@link java.util.Random} generator used by the {@link
 * java.lang.Math} class, a {@code ThreadLocalRandom} is initialized
 * with an internally generated seed that may not otherwise be
 * modified. When applicable, use of {@code ThreadLocalRandom} rather
 * than shared {@code Random} objects in concurrent programs will
 * typically encounter much less overhead and contention.  Use of
 * {@code ThreadLocalRandom} is particularly appropriate when multiple
 * tasks use random numbers in parallel in thread pools.
 *
 * <p>Usages of this class should typically be of the form:
 * {@code ThreadLocalRandom.current().nextX(...)} (where
 * {@code X} is {@code Int}, {@code Long}, etc).
 * When all usages are of this form, it is never possible to
 * accidently share a {@code ThreadLocalRandom} across multiple threads.
 *
 * <p>This class also provides additional commonly used bounded random
 * generation methods.
 *
 * @since 1.7
 * @author Doug Lea
 */
final class ThreadLocalRandom extends Random {
    // same constants as Random, but must be redeclared because private
    private final static long multiplier = 0x5DEECE66DL;
    private final static long addend = 0xBL;
    private final static long mask = (1L << 48) - 1;

    /**
     * The random seed. We can't use super.seed.
     */
    private long rnd;

    /**
     * Initialization flag to permit the first and only allowed call
     * to setSeed (inside Random constructor) to succeed.  We can't
     * allow others since it would cause setting seed in one part of a
     * program to unintentionally impact other usages by the thread.
     */
    private boolean initialized;

    // Padding to help avoid memory contention among seed updates in
    // different TLRs in the common case that they are located near
    // each other.
    @SuppressWarnings("unused")
    private long pad0, pad1, pad2, pad3, pad4, pad5, pad6, pad7;

    /**
     * The actual ThreadLocal
     */
    private static final ThreadLocal<ThreadLocalRandom> localRandom =
        new ThreadLocal<ThreadLocalRandom>() {
            @Override
            protected ThreadLocalRandom initialValue() {
                return new ThreadLocalRandom();
            }
    };


    /**
     * Constructor called only by localRandom.initialValue.
     * We rely on the fact that the superclass no-arg constructor
     * invokes setSeed exactly once to initialize.
     */
    ThreadLocalRandom() {
        super();
    }

    /**
     * Returns the current thread's {@code ThreadLocalRandom}.
     *
     * @return the current thread's {@code ThreadLocalRandom}
     */
    static ThreadLocalRandom current() {
        return localRandom.get();
    }

    /**
     * Throws {@code UnsupportedOperationException}.  Setting seeds in
     * this generator is not supported.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setSeed(long seed) {
        if (initialized) {
            throw new UnsupportedOperationException();
        }
        initialized = true;
        rnd = (seed ^ multiplier) & mask;
    }

    @Override
    protected int next(int bits) {
        rnd = rnd * multiplier + addend & mask;
        return (int) (rnd >>> 48-bits);
    }

    private static final long serialVersionUID = -5851777807851030925L;
}
