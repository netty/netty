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
package io.netty.channel.uring;

import io.netty.channel.IoEvent;
import io.netty.channel.IoRegistration;

import java.nio.ByteBuffer;

/**
 * {@link IoEvent} that will be produced as an result of a {@link IoUringIoOps}.
 */
public final class IoUringIoEvent implements IoEvent {

    private byte opcode;
    private int res;
    private int flags;
    private short data;
    private ByteBuffer extraCqeData;

    /**
     * Create a new instance
     *
     * @param res       the result.
     * @param flags     the flags
     * @param opcode    the op code
     * @param data      the user data that was given as part of the submission.
     */
    public IoUringIoEvent(int res, int flags, byte opcode, short data) {
        this.res = res;
        this.flags = flags;
        this.opcode = opcode;
        this.data = data;
    }

    // Used internally to reduce object creation
    void update(int res, int flags, byte opcode, short data, ByteBuffer extraCqeData) {
        this.res = res;
        this.flags = flags;
        this.opcode = opcode;
        this.data = data;
        this.extraCqeData = extraCqeData;
    }

    /**
     * Returns the result.
     *
     * @return  the result
     */
    public int res() {
        return res;
    }

    /**
     * Returns the flags.
     *
     * @return flags
     */
    public int flags() {
        return flags;
    }

    /**
     * Returns the op code of the {@link IoUringIoOps}.
     *
     * @return  opcode
     */
    public byte opcode() {
        return opcode;
    }

    /**
     * Returns the data that is passed as part of {@link IoUringIoOps}.
     *
     * @return  data.
     */
    public short data() {
        return data;
    }

    /**
     * Returns the extra data for the CQE. This will only be non-null of the ring was setup with
     * {@code IORING_SETUP_CQE32}. As this {@link ByteBuffer} maps into the shared completion queue its important
     * to not hold any reference to it outside of the {@link IoUringIoHandle#handle(IoRegistration, IoEvent)} method.
     *
     * @return extra data for the CQE or {@code null}.
     */
    public ByteBuffer extraCqeData() {
        return extraCqeData;
    }

    @Override
    public String toString() {
        return "IOUringIoEvent{" +
                "opcode=" + opcode +
                ", res=" + res +
                ", flags=" + flags +
                ", data=" + data +
                '}';
    }
}
