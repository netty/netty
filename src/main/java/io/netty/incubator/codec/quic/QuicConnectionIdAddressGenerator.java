/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;

/**
 * Creates a new {@link QuicConnectionIdAddress} instaces.
 */
public interface QuicConnectionIdAddressGenerator {
    /**
     * Creates a new {@link QuicConnectionIdAddress} with the maximum length.
     */
    QuicConnectionIdAddress newAddress();
    /**
     * Creates a new {@link QuicConnectionIdAddress} with the given length.
     */
    QuicConnectionIdAddress newAddress(int length);

    /**
     * Creates a new {@link QuicConnectionIdAddress} with the given length. The given input may be used to sign or
     * seed the the id, or may be ignored (depending on the implementation).
     */
    QuicConnectionIdAddress newAddress(ByteBuf input, int length);

    /**
     * Returns the maximum length of a connection id.
     */
    int maxConnectionIdLength();
}
