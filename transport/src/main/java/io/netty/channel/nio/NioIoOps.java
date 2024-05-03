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
package io.netty.channel.nio;

import io.netty.channel.IoOps;

import java.nio.channels.SelectionKey;

/**
 * Implementation of {@link IoOps} for
 * that is used by {@link NioIoHandler} and so for NIO based transports.
 */
public final class NioIoOps implements IoOps {
    /**
     * Interested in NO IO events.
     */
    public static final NioIoOps NONE = new NioIoOps(0);

    /**
     * Interested in IO events that should be handled by accepting new connections
     */
    public static final NioIoOps ACCEPT = new NioIoOps(SelectionKey.OP_ACCEPT);

    /**
     * Interested in IO events which should be handled by finish pending connect operations
     */
    public static final NioIoOps CONNECT = new NioIoOps(SelectionKey.OP_CONNECT);

    /**
     * Interested in IO events which tell that the underlying channel is writable again.
     */
    public static final NioIoOps WRITE = new NioIoOps(SelectionKey.OP_WRITE);

    /**
     * Interested in IO events which should be handled by reading data.
     */
    public static final NioIoOps READ = new NioIoOps(SelectionKey.OP_READ);

    /**
     * Interested in IO events which should be either handled by reading or accepting.
     */
    public static final NioIoOps READ_AND_ACCEPT = new NioIoOps(SelectionKey.OP_READ | SelectionKey.OP_ACCEPT);

    /**
     * Interested in IO events which should be either handled by reading or writing.
     */
    public static final NioIoOps READ_AND_WRITE = new NioIoOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

    // Just use an array to store often used values.
    private static final NioIoEvent[] EVENTS;

    static {
        NioIoOps all = new NioIoOps(
                NONE.value | ACCEPT.value | CONNECT.value | WRITE.value | READ.value);

        EVENTS = new NioIoEvent[all.value + 1];
        addToArray(EVENTS, NONE);
        addToArray(EVENTS, ACCEPT);
        addToArray(EVENTS, CONNECT);
        addToArray(EVENTS, WRITE);
        addToArray(EVENTS, READ);
        addToArray(EVENTS, READ_AND_ACCEPT);
        addToArray(EVENTS, READ_AND_WRITE);
        addToArray(EVENTS, all);
    }

    private static void addToArray(NioIoEvent[] array, NioIoOps opt) {
        array[opt.value] = new DefaultNioIoEvent(opt);
    }

    final int value;

    private NioIoOps(int value) {
        this.value = value;
    }

    /**
     * Returns {@code true} if this {@link NioIoOps} is a combination of the given {@link NioIoOps}.
     * @param ops   the ops.
     * @return      {@code true} if a combination of the given.
     */
    public boolean contains(NioIoOps ops) {
        return isIncludedIn(ops.value);
    }

    /**
     * Return a {@link NioIoOps} which is a combination of the current and the given {@link NioIoOps}.
     *
     * @param ops   the {@link NioIoOps} that should be added to this one.
     * @return      a {@link NioIoOps}.
     */
    public NioIoOps with(NioIoOps ops) {
        if (contains(ops)) {
            return this;
        }
        return valueOf(value | ops.value());
    }

    /**
     * Return a {@link NioIoOps} which is not a combination of the current and the given {@link NioIoOps}.
     *
     * @param ops   the {@link NioIoOps} that should be remove from this one.
     * @return      a {@link NioIoOps}.
     */
    public NioIoOps without(NioIoOps ops) {
        if (!contains(ops)) {
            return this;
        }
        return valueOf(value & ~ops.value());
    }

    /**
     * Returns the underlying ops value of the {@link NioIoOps}.
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
        NioIoOps nioOps = (NioIoOps) o;
        return value == nioOps.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    /**
     * Returns a {@link NioIoOps} for the given value.
     *
     * @param   value the value
     * @return  the {@link NioIoOps}.
     */
    public static NioIoOps valueOf(int value) {
        return eventOf(value).ops();
    }

    /**
     * Returns {@code true} if this {@link NioIoOps} is <strong>included </strong> in the given {@code ops}.
     *
     * @param ops   the ops to check.
     * @return      {@code true} if <strong>included</strong>, {@code false} otherwise.
     */
    public boolean isIncludedIn(int ops) {
        return (ops & value) != 0;
    }

    /**
     * Returns {@code true} if this {@link NioIoOps} is <strong>not included</strong> in the given {@code ops}.
     *
     * @param ops   the ops to check.
     * @return      {@code true} if <strong>not included</strong>, {@code false} otherwise.
     */
    public boolean isNotIncludedIn(int ops) {
        return (ops & value) == 0;
    }

    static NioIoEvent eventOf(int value) {
        if (value > 0 && value < EVENTS.length) {
            NioIoEvent event = EVENTS[value];
            if (event != null) {
                return event;
            }
        }
        return new DefaultNioIoEvent(new NioIoOps(value));
    }

    private static final class DefaultNioIoEvent implements NioIoEvent {
        private final NioIoOps ops;

        DefaultNioIoEvent(NioIoOps ops) {
            this.ops = ops;
        }

        @Override
        public NioIoOps ops() {
            return ops;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NioIoEvent event = (NioIoEvent) o;
            return event.ops().equals(ops());
        }

        @Override
        public int hashCode() {
            return ops().hashCode();
        }
    }
}
