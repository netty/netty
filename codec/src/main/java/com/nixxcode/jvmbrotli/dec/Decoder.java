/* Copyright 2017 Google Inc. All Rights Reserved.

   Distributed under MIT license.
   See file LICENSE for detail or copy at https://opensource.org/licenses/MIT
*/

package com.nixxcode.jvmbrotli.dec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Base class for InputStream / Channel implementations.
 */
public class Decoder {

    /**
     * Decodes the given data buffer.
     */
    public static byte[] decompress(byte[] data) throws IOException {
        DecoderJNI.Wrapper decoder = new DecoderJNI.Wrapper(data.length);
        ArrayList<byte[]> output = new ArrayList<byte[]>();
        int totalOutputSize = 0;
        try {
            decoder.getInputBuffer().put(data);
            decoder.push(data.length);
            while (decoder.getStatus() != DecoderJNI.Status.DONE) {
                switch (decoder.getStatus()) {
                    case OK:
                        decoder.push(0);
                        break;

                    case NEEDS_MORE_OUTPUT:
                        ByteBuffer buffer = decoder.pull();
                        byte[] chunk = new byte[buffer.remaining()];
                        buffer.get(chunk);
                        output.add(chunk);
                        totalOutputSize += chunk.length;
                        break;

                    case NEEDS_MORE_INPUT:
                        return null;

                    default:
                        throw new IOException("Corrupted Input");
                }
            }
        } finally {
            decoder.destroy();
        }
        if (output.size() == 1) {
            return output.get(0);
        }
        byte[] result = new byte[totalOutputSize];
        int offset = 0;
        for (byte[] chunk : output) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }
}
