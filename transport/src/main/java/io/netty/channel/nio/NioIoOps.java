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
    private static final NioIoOps[] OPTS;

    static {
        NioIoOps all = new NioIoOps(
                NONE.value | ACCEPT.value | CONNECT.value | WRITE.value | READ.value);
        OPTS = new NioIoOps[all.value + 1];
        addToArray(OPTS, NONE);
        addToArray(OPTS, ACCEPT);
        addToArray(OPTS, CONNECT);
        addToArray(OPTS, WRITE);
        addToArray(OPTS, READ);
        addToArray(OPTS, READ_AND_ACCEPT);
        addToArray(OPTS, READ_AND_WRITE);
        addToArray(OPTS, all);
    }

    private static void addToArray(NioIoOps[] array, NioIoOps opt) {
        array[opt.value] = opt;
    }

    final int value;

    private NioIoOps(int value) {
        this.value = value;
    }

    /**
     * Returns {@code true} if this {@link NioIoOps} is a combination of the given {@link NioIoOps}.
     * @param opt   the opt.
     * @return      {@code true} if a combination of the given.
     */
    public boolean contains(NioIoOps opt) {
        return (value & opt.value) != 0;
    }

    /**
     * Return a {@link NioIoOps} which is a combination of the current and the given {@link NioIoOps}.
     *
     * @param opt   the {@link NioIoOps} that should be added to this one.
     * @return      a {@link NioIoOps}.
     */
    public NioIoOps with(NioIoOps opt) {
        if (contains(opt)) {
            return this;
        }
        return valueOf(value | opt.value());
    }

    /**
     * Return a {@link NioIoOps} which is not a combination of the current and the given {@link NioIoOps}.
     *
     * @param opt   the {@link NioIoOps} that should be remove from this one.
     * @return      a {@link NioIoOps}.
     */
    public NioIoOps without(NioIoOps opt) {
        if (!contains(opt)) {
            return this;
        }
        return valueOf(value & ~opt.value());
    }

    /**
     * Returns the underlying value of the {@link NioIoOps}.
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
        NioIoOps nioOpt = (NioIoOps) o;
        return value == nioOpt.value;
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
        final NioIoOps opt;
        if (value < OPTS.length) {
            opt = OPTS[value];
            if (opt != null) {
                return opt;
            }
        }
        return new NioIoOps(value);
    }
}
