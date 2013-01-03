/*
 * Copyright 2013 The Netty Project
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
package io.netty.buffer;

/**
 * A packet which is send or receive. The contract for a {@link ByteBufHolder} is the
 * following:
 *
 * When send a {@link ByteBufHolder} the {@link ByteBufHolder} will be freed by calling {@link #free()}
 * in the actual transport implementation. When receive a {@link ByteBufHolder} the {@link #free()}
 * must be called once is is processed.
 *
 */
public interface ByteBufHolder extends Freeable {

    /**
     * Return the data which is held by this {@link ByteBufHolder}.
     *
     */
    ByteBuf data();

    /**
     * Create a copy of this {@link ByteBufHolder} which can be used even after {@link #free()}
     * is called.
     */
    ByteBufHolder copy();

    /**
     * Free of the resources that are hold by this instance. This includes the {@link ByteBuf}.
     */
    @Override
    void free();

    /**
     * Returns {@code true} if and only if this instances was freed.
     */
    @Override
    boolean isFreed();
}
