/*
 * Copyright 2024 The Netty Project
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

import com.aayushatharva.brotli4j.encoder.Encoder;

import static com.aayushatharva.brotli4j.encoder.Encoder.Mode;

/**
 * Provides a way to specify the Brotli compression mode.
 */
public enum BrotliMode {

    /**
     * The compressor does not make any assumptions about the input data's properties,
     * making it suitable for a wide range of data types.
     * default mode.
     */
    GENERIC,

    /**
     * Optimized for UTF-8 formatted text input.
     */
    TEXT,

    /**
     * Designed specifically for font data compression, as used in WOFF 2.0.
     */
    FONT;

    /**
     * Convert to Brotli {@link Encoder.Mode}.
     *
     * @return a new {@link Encoder.Mode}
     */
    Mode adapt() {
        switch (this) {
            case GENERIC:
                return Mode.GENERIC;
            case TEXT:
                return Mode.TEXT;
            case FONT:
                return Mode.FONT;
            default:
                throw new IllegalStateException("Unsupported enum value: " + this);
        }
    }
}
