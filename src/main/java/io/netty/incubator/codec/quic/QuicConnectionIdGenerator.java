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

import java.nio.ByteBuffer;

/**
 * Creates new connection id instances.
 */
public interface QuicConnectionIdGenerator {
    /**
     * Creates a new {@link QuicConnectionAddress} with the given length.
     *
     * @param length    the length of the id.
     * @return          the id.
     */
    ByteBuffer newId(int length);

    /**
     * Creates a new connection id with the given length. The given input may be used to sign or
     * seed the id, or may be ignored (depending on the implementation).
     *
     * @param input     the input which may be used to generate the id.
     * @param length    the length of the id.
     * @return          the id.
     */
    ByteBuffer newId(ByteBuffer input, int length);

    /**
     * Returns the maximum length of a connection id.
     *
     * @return the maximum length of a connection id that is supported.
     */
    int maxConnectionIdLength();

    /**
     * Return a {@link QuicConnectionIdGenerator} which randomly generates new connection ids.
     *
     * @return a {@link QuicConnectionIdGenerator} which randomly generated ids.
     */
    static QuicConnectionIdGenerator randomGenerator() {
        return SecureRandomQuicConnectionIdGenerator.INSTANCE;
    }
}
