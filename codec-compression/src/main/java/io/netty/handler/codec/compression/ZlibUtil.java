/*
 * Copyright 2012 The Netty Project
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

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.Inflater;
import com.jcraft.jzlib.JZlib;

/**
 * Utility methods used by {@link JZlibEncoder} and {@link JZlibDecoder}.
 */
final class ZlibUtil {

    static void fail(Inflater z, String message, int resultCode) {
        throw inflaterException(z, message, resultCode);
    }

    static void fail(Deflater z, String message, int resultCode) {
        throw deflaterException(z, message, resultCode);
    }

    static DecompressionException inflaterException(Inflater z, String message, int resultCode) {
        return new DecompressionException(message + " (" + resultCode + ')' + (z.msg != null? ": " + z.msg : ""));
    }

    static CompressionException deflaterException(Deflater z, String message, int resultCode) {
        return new CompressionException(message + " (" + resultCode + ')' + (z.msg != null? ": " + z.msg : ""));
    }

    static JZlib.WrapperType convertWrapperType(ZlibWrapper wrapper) {
        switch (wrapper) {
        case NONE:
            return JZlib.W_NONE;
        case ZLIB:
            return JZlib.W_ZLIB;
        case GZIP:
            return JZlib.W_GZIP;
        case ZLIB_OR_NONE:
            return JZlib.W_ANY;
        default:
            throw new Error("Unexpected wrapper type: " + wrapper);
        }
    }

    static int wrapperOverhead(ZlibWrapper wrapper) {
        switch (wrapper) {
        case NONE:
            return 0;
        case ZLIB:
        case ZLIB_OR_NONE:
            return 2;
        case GZIP:
            return 10;
        default:
            throw new Error("Unexpected wrapper type: " + wrapper);
        }
    }

    private ZlibUtil() {
    }
}
