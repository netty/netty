/*
 * Copyright 2022 The Netty Project
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

/**
 * {@link IoEvent} for io_uring.
 */
public final class IOUringIoEvent implements IoEvent {

    private byte opcode;
    private int id;
    private int res;
    private int flags;
    private short data;

    /**
     * Create a new instance
     *
     * @param res       the result.
     * @param flags     the flags
     * @param opcode    the op code
     * @param id        the id that was given as part of the submission.
     * @param data      the user data that was given as part of the submission.
     */
    public IOUringIoEvent(int res, int flags, byte opcode, int id, short data) {
        this.res = res;
        this.flags = flags;
        this.opcode = opcode;
        this.id = id;
        this.data = data;
    }

    // Use internally to reduce object creation
    void update(int res, int flags, byte opcode, int id, short data) {
        this.res = res;
        this.flags = flags;
        this.opcode = opcode;
        this.id = id;
        this.data = data;
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
     * Returns the op code of the {@link IOUringIoOps#opcode()}.
     *
     * @return  opcode
     */
    public byte opcode() {
        return opcode;
    }

    /**
     * Returns the data that is passed as part of {@link IOUringIoOps#data()}.
     *
     * @return  data.
     */
    public short data() {
        return data;
    };

    /**
     * Returns the id that is passed as part of {@link IOUringIoOps#id()}.
     *
     * @return  id.
     */
    public int id() {
        return id;
    }

    @Override
    public String toString() {
        return "IOUringIoEvent{" +
                "opcode=" + opcode +
                ", id=" + id +
                ", res=" + res +
                ", flags=" + flags +
                ", data=" + data +
                '}';
    }
}
