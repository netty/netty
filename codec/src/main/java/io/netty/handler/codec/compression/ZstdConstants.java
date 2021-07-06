/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.compression;

final class ZstdConstants {

    static final int DEFAULT_COMPRESSION_LEVEL = 10;

    /**
     * Max block size
     */
    static final int MAX_BLOCK_SIZE = 1 << DEFAULT_COMPRESSION_LEVEL + 0x0F;   //  32 M
    /**
     * Default block size
     */
    static final int DEFAULT_BLOCK_SIZE = 1 << 16;  // 64 KB

    private ZstdConstants() { }
}
