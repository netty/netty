/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;

/**
 * A {@link ByteBuffer} pool dedicated for {@link SslHandler} performance
 * improvement.
 *
 * The reason why {@link SslHandler} requires a buffer pool is because the
 * current {@link SSLEngine} implementation always requires a 17KiB buffer for
 * every 'wrap' and 'unwrap' operation.  In most cases, the actual size of the
 * required buffer is much smaller than that, and therefore allocating a 17KiB
 * buffer for every 'wrap' and 'unwrap' operation wastes a lot of memory
 * bandwidth, resulting in the application performance degradation.
 */
public interface SslBufferPool {

    // Returned buffers must be large enough to accomodate the maximum SSL record size.
    // Header (5) + Data (2^14) + Compression (1024) + Encryption (1024) + MAC (20) + Padding (256)
    int MAX_PACKET_SIZE = 18713;

    /**
     * Acquire a new {@link ByteBuffer} out of the {@link SslBufferPool}
     */
    ByteBuffer acquireBuffer();

    /**
     * Release a previously acquired {@link ByteBuffer}
     *
     * @param buffer
     */
    void releaseBuffer(ByteBuffer buffer);
}
