/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.quic;

import java.nio.ByteBuffer;

/**
 * Generate
 * <a href="https://www.ietf.org/archive/id/draft-ietf-quic-transport-29.html#name-calculating-a-stateless-res">
 *     stateless reset tokens</a> to use.
 */
public interface QuicResetTokenGenerator {

    /**
     * Generate a reset token to use for the given connection id. The returned token MUST be of length 16.
     * @param cid the connection id
     * @return a newly generated reset token
     */
    ByteBuffer newResetToken(ByteBuffer cid);

    /**
     * Return a {@link QuicResetTokenGenerator} which generates new reset tokens by signing the given input.
     *
     * @return a {@link QuicResetTokenGenerator} which generates new reset tokens by signing the given input.
     */
    static QuicResetTokenGenerator signGenerator() {
        return HmacSignQuicResetTokenGenerator.INSTANCE;
    }
}
