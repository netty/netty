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
package io.netty.handler.stream;

import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChunkedStreamTest {

    @Test
    public void writeTest() throws Exception {
        ChunkedStream chunkedStream = new ChunkedStream(new InputStream() {
            @Override
            public int read() {
                return -1;
            }

            @Override
            public int available() {
                return 1;
            }
        });

        assertFalse(chunkedStream.isEndOfInput());
        assertNull(chunkedStream.readChunk(UnpooledByteBufAllocator.DEFAULT));
        assertEquals(0, chunkedStream.progress());
        chunkedStream.close();
        assertTrue(chunkedStream.isEndOfInput());
        assertNull(chunkedStream.readChunk(UnpooledByteBufAllocator.DEFAULT));
    }
}
