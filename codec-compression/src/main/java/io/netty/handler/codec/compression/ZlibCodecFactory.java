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

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Creates a new {@link ZlibEncoder} and a new {@link ZlibDecoder}.
 */
public final class ZlibCodecFactory {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ZlibCodecFactory.class);

    private static final int DEFAULT_JDK_WINDOW_SIZE = 15;
    private static final int DEFAULT_JDK_MEM_LEVEL = 8;

    private static final boolean noJdkZlibDecoder;
    private static final boolean noJdkZlibEncoder;

    private static final boolean JZLIB_AVAILABLE;

    static {
        noJdkZlibDecoder = SystemPropertyUtil.getBoolean("io.netty.noJdkZlibDecoder", false);
        logger.debug("-Dio.netty.noJdkZlibDecoder: {}", noJdkZlibDecoder);

        noJdkZlibEncoder = SystemPropertyUtil.getBoolean("io.netty.noJdkZlibEncoder", false);
        logger.debug("-Dio.netty.noJdkZlibEncoder: {}", noJdkZlibEncoder);

        boolean jzlibAvailable;
        try {
            Class.forName("com.jcraft.jzlib.JZlib", false,
                PlatformDependent.getClassLoader(ZlibCodecFactory.class));
            jzlibAvailable = true;
        } catch (ClassNotFoundException t) {
            jzlibAvailable = false;
            logger.debug(
                "JZlib not in the classpath; the only window bits supported value will be " +
                    DEFAULT_JDK_WINDOW_SIZE);
        }
        JZLIB_AVAILABLE = jzlibAvailable;
    }

    /**
     * Returns {@code true} if specify a custom window size and mem level is supported.
     */
    public static boolean isSupportingWindowSizeAndMemLevel() {
        return JZLIB_AVAILABLE;
    }

    public static ZlibEncoder newZlibEncoder(int compressionLevel) {
        if (noJdkZlibEncoder) {
            return new JZlibEncoder(compressionLevel);
        } else {
            return new JdkZlibEncoder(compressionLevel);
        }
    }

    public static ZlibEncoder newZlibEncoder(ZlibWrapper wrapper) {
        if (noJdkZlibEncoder) {
            return new JZlibEncoder(wrapper);
        } else {
            return new JdkZlibEncoder(wrapper);
        }
    }

    public static ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel) {
        if (noJdkZlibEncoder) {
            return new JZlibEncoder(wrapper, compressionLevel);
        } else {
            return new JdkZlibEncoder(wrapper, compressionLevel);
        }
    }

    public static ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel) {
        if (noJdkZlibEncoder ||
                windowBits != DEFAULT_JDK_WINDOW_SIZE || memLevel != DEFAULT_JDK_MEM_LEVEL) {
            return new JZlibEncoder(wrapper, compressionLevel, windowBits, memLevel);
        } else {
            return new JdkZlibEncoder(wrapper, compressionLevel);
        }
    }

    public static ZlibEncoder newZlibEncoder(byte[] dictionary) {
        if (noJdkZlibEncoder) {
            return new JZlibEncoder(dictionary);
        } else {
            return new JdkZlibEncoder(dictionary);
        }
    }

    public static ZlibEncoder newZlibEncoder(int compressionLevel, byte[] dictionary) {
        if (noJdkZlibEncoder) {
            return new JZlibEncoder(compressionLevel, dictionary);
        } else {
            return new JdkZlibEncoder(compressionLevel, dictionary);
        }
    }

    public static ZlibEncoder newZlibEncoder(int compressionLevel, int windowBits, int memLevel, byte[] dictionary) {
        if (noJdkZlibEncoder ||
                windowBits != DEFAULT_JDK_WINDOW_SIZE || memLevel != DEFAULT_JDK_MEM_LEVEL) {
            return new JZlibEncoder(compressionLevel, windowBits, memLevel, dictionary);
        } else {
            return new JdkZlibEncoder(compressionLevel, dictionary);
        }
    }

    public static ZlibDecoder newZlibDecoder() {
        if (noJdkZlibDecoder) {
            return new JZlibDecoder();
        } else {
            return new JdkZlibDecoder(true);
        }
    }

    public static ZlibDecoder newZlibDecoder(ZlibWrapper wrapper) {
        if (noJdkZlibDecoder) {
            return new JZlibDecoder(wrapper);
        } else {
            return new JdkZlibDecoder(wrapper, true);
        }
    }

    public static ZlibDecoder newZlibDecoder(byte[] dictionary) {
        if (noJdkZlibDecoder) {
            return new JZlibDecoder(dictionary);
        } else {
            return new JdkZlibDecoder(dictionary);
        }
    }

    private ZlibCodecFactory() {
        // Unused
    }
}
