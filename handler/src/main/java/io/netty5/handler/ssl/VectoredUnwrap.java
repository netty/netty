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
package io.netty5.handler.ssl;

import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;

/**
 * Optional extra interface that {@link javax.net.ssl.SSLEngine} sub-classes can implement, if they support a faster
 * way of unwrapping multiple input buffers into multiple output buffers.
 */
public interface VectoredUnwrap {
    /**
     * Similar to {@link javax.net.ssl.SSLEngine#unwrap(ByteBuffer, ByteBuffer[])}, but takes an array of input buffers.
     *
     * @param srcs The array of source buffers with data to unwrap.
     * @param dsts The array of destination buffers where the unwrapped data should be written.
     * @return The {@link SSLEngineResult} of the unwrap operation.
     * @throws SSLException A problem was encountered during data processing that caused the
     * {@link javax.net.ssl.SSLEngine} to abort.
     * @see javax.net.ssl.SSLEngine#unwrap(ByteBuffer, ByteBuffer[])
     */
    SSLEngineResult unwrap(ByteBuffer[] srcs, ByteBuffer[] dsts) throws SSLException;
}
