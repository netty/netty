/*
 * Copyright 2026 The Netty Project
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
package io.netty.channel.unix;

/**
 * A generic control message, which is identified by its {@code cmsg_level} and {@code cmsg_type}. See
 * <a href="https://man7.org/linux/man-pages/man3/cmsg.3.html">cmsg(3)</a>.
 * <p>
 * This can be used to send and receive control messages that are not supported by Netty out of the box. When
 * receiving, the user is responsible to enable the reception of the control message (usually via a socket option,
 * which can be set via {@link IntegerUnixChannelOption} or {@link RawUnixChannelOption}).
 *
 * @param <T> the type of the value of the control message.
 */
public abstract class GenericControlMessage<T> extends ControlMessage<T> {

    private final int level;
    private final int type;

    GenericControlMessage(String name, int level, int type) {
        super(name);
        this.level = level;
        this.type = type;
    }

    /**
     * Returns the {@code cmsg_level} of the control message.
     *
     * @return the level.
     */
    public final int level() {
        return level;
    }

    /**
     * Returns the {@code cmsg_type} of the control message.
     *
     * @return the type.
     */
    public final int type() {
        return type;
    }

    @Override
    final boolean isSendSupported() {
        return true;
    }

    @Override
    final int sendLevel() {
        return level;
    }

    @Override
    final int sendType() {
        return type;
    }

    @Override
    final boolean isReceiveSupported() {
        return true;
    }

    @Override
    final boolean isReceived(int level, int type) {
        return this.level == level && this.type == type;
    }
}
