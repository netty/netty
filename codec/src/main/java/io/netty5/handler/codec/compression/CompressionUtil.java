/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.codec.compression;

import io.netty5.buffer.api.Buffer;


final class CompressionUtil {

    private CompressionUtil() { }

    static void checkChecksum(ByteBufChecksum checksum, Buffer uncompressed, int currentChecksum) {
        checksum.reset();
        checksum.update(uncompressed,
                uncompressed.readerOffset(), uncompressed.readableBytes());

        final int checksumResult = (int) checksum.getValue();
        if (checksumResult != currentChecksum) {
            throw new DecompressionException(String.format(
                    "stream corrupted: mismatching checksum: %d (expected: %d)",
                    checksumResult, currentChecksum));
        }
    }
}
