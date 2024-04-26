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

import io.netty.channel.IoOpt;

import java.nio.channels.SelectionKey;

/**
 * Implementation of {@link IoOpt} for
 * that is used by {@link NioIoHandler} and so for NIO based transports.
 */
public final class NioOpt implements IoOpt {
    /**
     * Interested in NO IO events.
     */
    public static final NioOpt NONE = new NioOpt(0);

    /**
     * Interested in IO events that should be handled by accepting new connections
     */
    public static final NioOpt ACCEPT = new NioOpt(SelectionKey.OP_ACCEPT);

    /**
     * Interested in IO events which should be handled by finish pending connect operations
     */
    public static final NioOpt CONNECT = new NioOpt(SelectionKey.OP_CONNECT);

    /**
     * Interested in IO events which tell that the underlying channel is writable again.
     */
    public static final NioOpt WRITE = new NioOpt(SelectionKey.OP_WRITE);

    /**
     * Interested in IO events which should be handled by reading data.
     */
    public static final NioOpt READ = new NioOpt(SelectionKey.OP_READ);

    /**
     * Interested in IO events which should be either handled by reading or accepting.
     */
    public static final NioOpt READ_AND_ACCEPT = new NioOpt(SelectionKey.OP_READ | SelectionKey.OP_ACCEPT);

    /**
     * Interested in IO events which should be either handled by reading or writing.
     */
    public static final NioOpt READ_AND_WRITE = new NioOpt(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

    // Just use an array to store often used values.
    private static final NioOpt[] OPTS;

    static {
        NioOpt all = new NioOpt(
                NONE.value | ACCEPT.value | CONNECT.value | WRITE.value | READ.value);
        OPTS = new NioOpt[all.value + 1];
        addToArray(OPTS, NONE);
        addToArray(OPTS, ACCEPT);
        addToArray(OPTS, CONNECT);
        addToArray(OPTS, WRITE);
        addToArray(OPTS, READ);
        addToArray(OPTS, READ_AND_ACCEPT);
        addToArray(OPTS, READ_AND_WRITE);
        addToArray(OPTS, all);
    }

    private static void addToArray(NioOpt[] array, NioOpt opt) {
        array[opt.value] = opt;
    }

    final int value;

    private NioOpt(int value) {
        this.value = value;
    }

    /**
     * Returns {@code true} if this {@link NioOpt} is a combination of the given {@link NioOpt}.
     * @param opt   the opt.
     * @return      {@code true} if a combination of the given.
     */
    public boolean contains(NioOpt opt) {
        return (value & opt.value) != 0;
    }

    /**
     * Return a {@link NioOpt} which is a combination of the current and the given {@link NioOpt}.
     *
     * @param opt   the {@link NioOpt} that should be added to this one.
     * @return      a {@link NioOpt}.
     */
    public NioOpt with(NioOpt opt) {
        if (contains(opt)) {
            return this;
        }
        return valueOf(value | opt.value());
    }

    /**
     * Return a {@link NioOpt} which is not a combination of the current and the given {@link NioOpt}.
     *
     * @param opt   the {@link NioOpt} that should be remove from this one.
     * @return      a {@link NioOpt}.
     */
    public NioOpt without(NioOpt opt) {
        if (!contains(opt)) {
            return this;
        }
        return valueOf(value & ~opt.value());
    }

    /**
     * Returns the underlying value of the {@link NioOpt}.
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
        NioOpt nioOpt = (NioOpt) o;
        return value == nioOpt.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    /**
     * Returns a {@link NioOpt} for the given value.
     *
     * @param   value the value
     * @return  the {@link NioOpt}.
     */
    public static NioOpt valueOf(int value) {
        final NioOpt opt;
        if (value < OPTS.length) {
            opt = OPTS[value];
            if (opt != null) {
                return opt;
            }
        }
        return new NioOpt(value);
    }
}
