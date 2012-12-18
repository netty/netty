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
package io.netty.handler.codec.compression.snappy;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.compression.CompressionException;

import java.util.zip.CRC32;

public final class SnappyChecksumUtil {
    /**
     * Computes the CRC32 checksum of the supplied data, performs the "mask" operation
     * on the computed checksum, and then compares the resulting masked checksum to the
     * supplied checksum.
     *
     * @param slice The input data to calculate the CRC32 checksum of
     * @param checksum The checksum decoded from the stream to compare against
     * @throws CompressionException If the calculated and supplied checksums do not match
     */
    public static void validateChecksum(ByteBuf slice, int checksum) {
        if (calculateChecksum(slice) != checksum) {
            throw new CompressionException("Uncompressed data did not match checksum");
        }
    }

    /**
     * Computes the CRC32 checksum of the supplied data and performs the "mask" operation
     * on the computed checksum
     *
     * @param slice The input data to calculate the CRC32 checksum of
     */
    public static int calculateChecksum(ByteBuf slice) {
        CRC32 crc32 = new CRC32();
        try {
            if (slice.hasArray()) {
                crc32.update(slice.array());
            } else {
                byte[] array = new byte[slice.readableBytes()];
                slice.markReaderIndex();
                slice.readBytes(array);
                slice.resetReaderIndex();
                crc32.update(array);
            }

            return maskChecksum((int) crc32.getValue());
        } finally {
            crc32.reset();
        }
    }

    /**
     * From the spec:
     *
     * "Checksums are not stored directly, but masked, as checksumming data and
     * then its own checksum can be problematic. The masking is the same as used
     * in Apache Hadoop: Rotate the checksum by 15 bits, then add the constant
     * 0xa282ead8 (using wraparound as normal for unsigned integers)."
     *
     * @param checksum The actual checksum of the data
     * @return The masked checksum
     */
    static int maskChecksum(int checksum) {
        return (checksum >> 15 | checksum << 17) + 0xa282ead8;
    }

    // utility class
    private SnappyChecksumUtil() { }
}
