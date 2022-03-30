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
package io.netty.channel.unix;


import io.netty.util.internal.ObjectUtil;

import java.nio.ByteBuffer;

/**
 * A {@link GenericUnixChannelOption} which uses an {@link ByteBuffer} as {@code optval}. The user is responsible
 * to fill the {@link ByteBuffer} in a correct manner, so it works with the {@param level} and {@param optname}.
 */
public final class RawUnixChannelOption extends GenericUnixChannelOption<ByteBuffer> {

    private final int length;

    /**
     * Creates a new instance.
     *
     * @param name      the name that is used.
     * @param level     the level.
     * @param length    the expected length of the optvalue.
     * @param optname   the optname.
     */
    public RawUnixChannelOption(String name, int level, int optname, int length) {
        super(name, level, optname);
        this.length = ObjectUtil.checkPositive(length, "length");
    }

    /**
     * The length of the optval.
     *
     * @return the length.
     */
    public int length() {
        return length;
    }

    @Override
    public void validate(ByteBuffer value) {
        super.validate(value);
        if (value.remaining() != length) {
            throw new IllegalArgumentException("Length of value does not match. Expected "
                    + length + ", but got " + value.remaining());
        }
    }
}
