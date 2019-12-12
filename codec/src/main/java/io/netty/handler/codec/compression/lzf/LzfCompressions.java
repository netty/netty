/*
 * Copyright 2009-2010 Ning, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.compression.lzf;

import com.ning.compress.lzf.LZFChunk;
import io.netty.buffer.ByteBuf;

/**
 * LzfCompressions
 */
public final class LzfCompressions {

    /**
     * input heap byteBuf
     * output direct byteBuf
     */
    public static int appendEncoded(ChunkEncoder enc,
                                    byte[] input,
                                    int inputPtr,
                                    int inputLength,
                                    ByteBuf outputBuffer) {

        int outputPtr = 0;
        int left = inputLength;
        int chunkLen = Math.min(LZFChunk.MAX_CHUNK_LEN, left);

        outputPtr = enc.appendEncodedChunk(input, inputPtr, chunkLen, outputBuffer, outputPtr);
        left -= chunkLen;
        // shortcut: if it all fit in, no need to coalesce:
        if (left < 1) {
            return outputPtr;
        }
        // otherwise need to keep on encoding...
        inputPtr += chunkLen;
        do {
            chunkLen = Math.min(left, LZFChunk.MAX_CHUNK_LEN);
            outputPtr = enc.appendEncodedChunk(input, inputPtr, chunkLen, outputBuffer, outputPtr);
            inputPtr += chunkLen;
            left -= chunkLen;
        } while (left > 0);
        return outputPtr;
    }

    /**
     * input direct byteBuf
     * output direct byteBuf
     */
    public static int appendEncoded(ChunkEncoder enc,
                                    ByteBuf input,
                                    int inputLength,
                                    ByteBuf outputBuffer) {

        int inputPtr = 0;
        int outputPtr = 0;

        int left = inputLength;
        int chunkLen = Math.min(LZFChunk.MAX_CHUNK_LEN, left);

        outputPtr = enc.appendEncodedChunk(input, inputPtr, chunkLen, outputBuffer, outputPtr);
        left -= chunkLen;
        // shortcut: if it all fit in, no need to coalesce:
        if (left < 1) {
            return outputPtr;
        }
        // otherwise need to keep on encoding...
        inputPtr += chunkLen;
        do {
            chunkLen = Math.min(left, LZFChunk.MAX_CHUNK_LEN);
            outputPtr = enc.appendEncodedChunk(input, inputPtr, chunkLen, outputBuffer, outputPtr);
            inputPtr += chunkLen;
            left -= chunkLen;
        } while (left > 0);
        return outputPtr;
    }
}
