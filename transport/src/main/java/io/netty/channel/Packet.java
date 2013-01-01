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
package io.netty.channel;

import io.netty.buffer.ByteBuf;

/**
 * A packet which is send or receive. The contract for a {@link Packet} is the
 * following:
 *
 * When send a {@link Packet} the {@link Packet} will be freed by calling {@link #free()}
 * in the actual transport implementation. When receive a {@link Packet} the {@link #free()}
 * must be called once is is processed. There are special {@link ChannelHandler} which take care of
 * this like:
 *  - {@link ChannelInboundPacketHandler}
 *
 */
public interface Packet {

    /**
     * Return the data which is held by this {@link Packet}.
     *
     */
    ByteBuf data();

    /**
     * Free all resources which are held by this {@link Packet}.
     */
    void free();

    /**
     * Return {@code true} if the {@link Packet} was freed already.
     */
    boolean isFreed();

    /**
     * Create a copy of this {@link Packet} which can be used even after {@link #free()}
     * is called.
     */
    Packet copy();
}
