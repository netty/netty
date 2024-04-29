/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.IoOpt;

/**
 * Implementation of {@link IoOpt} that is used by {@link EpollIoHandler} and so for epoll based transports.
 */
public final class EpollIoOpt implements IoOpt {

    static {
        // Need to ensure we load the native lib before trying to use the values in Native to construct the different
        // instances.
        Epoll.ensureAvailability();
    }

    /**
     * Interested in IO events that should be handled by accepting new connections
     */
    public static final EpollIoOpt EPOLLOUT = new EpollIoOpt(Native.EPOLLOUT);

    /**
     * Interested in IO events which should be handled by finish pending connect operations
     */
    public static final EpollIoOpt EPOLLIN = new EpollIoOpt(Native.EPOLLIN);

    /**
     * Interested in IO events which tell that the underlying channel is writable again.
     */
    public static final EpollIoOpt EPOLLERR = new EpollIoOpt(Native.EPOLLERR);

    /**
     * Interested in IO events which should be handled by reading data.
     */
    public static final EpollIoOpt EPOLLRDHUP = new EpollIoOpt(Native.EPOLLRDHUP);

    public static final EpollIoOpt EPOLLET = new EpollIoOpt(Native.EPOLLET);

    // Just use an array to store often used values.
    private static final EpollIoOpt[] OPTS;

    static {
        EpollIoOpt all = new EpollIoOpt(
                EPOLLOUT.value | EPOLLIN.value | EPOLLERR.value | EPOLLRDHUP.value);
        OPTS = new EpollIoOpt[all.value + 1];
        addToArray(OPTS, EPOLLOUT);
        addToArray(OPTS, EPOLLIN);
        addToArray(OPTS, EPOLLERR);
        addToArray(OPTS, EPOLLRDHUP);
        addToArray(OPTS, all);
    }

    private static void addToArray(EpollIoOpt[] array, EpollIoOpt opt) {
        array[opt.value] = opt;
    }

    final int value;

    private EpollIoOpt(int value) {
        this.value = value;
    }

    /**
     * Returns {@code true} if this {@link EpollIoOpt} is a combination of the given {@link EpollIoOpt}.
     * @param opt   the opt.
     * @return      {@code true} if a combination of the given.
     */
    public boolean contains(EpollIoOpt opt) {
        return (value & opt.value) != 0;
    }

    /**
     * Return a {@link EpollIoOpt} which is a combination of the current and the given {@link EpollIoOpt}.
     *
     * @param opt   the {@link EpollIoOpt} that should be added to this one.
     * @return      a {@link EpollIoOpt}.
     */
    public EpollIoOpt with(EpollIoOpt opt) {
        if (contains(opt)) {
            return this;
        }
        return valueOf(value | opt.value());
    }

    /**
     * Return a {@link EpollIoOpt} which is not a combination of the current and the given {@link EpollIoOpt}.
     *
     * @param opt   the {@link EpollIoOpt} that should be remove from this one.
     * @return      a {@link EpollIoOpt}.
     */
    public EpollIoOpt without(EpollIoOpt opt) {
        if (!contains(opt)) {
            return this;
        }
        return valueOf(value & ~opt.value());
    }

    /**
     * Returns the underlying value of the {@link EpollIoOpt}.
     *
     * @return value.
     */
    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EpollIoOpt nioOpt = (EpollIoOpt) o;
        return value == nioOpt.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    /**
     * Returns a {@link EpollIoOpt} for the given value.
     *
     * @param   value the value
     * @return  the {@link EpollIoOpt}.
     */
    public static EpollIoOpt valueOf(int value) {
        final EpollIoOpt opt;
        if (value > 0 && value < OPTS.length) {
            opt = OPTS[value];
            if (opt != null) {
                return opt;
            }
        } else if (value == EPOLLET.value) {
            return EPOLLET;
        }
        return new EpollIoOpt(value);
    }
}
