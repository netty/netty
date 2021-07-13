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

import com.aayushatharva.brotli4j.encoder.Encoder;
import io.netty.util.internal.ObjectUtil;

/**
 * {@link BrotliOptions} holds {@link Encoder.Parameters} for
 * Brotli compression.
 */
public final class BrotliOptions implements CompressionOptions {

    private final Encoder.Parameters parameters;

    /**
     * @see StandardCompressionOptions#brotli()
     */
    static final BrotliOptions DEFAULT = new BrotliOptions(
            new Encoder.Parameters().setQuality(4).setMode(Encoder.Mode.TEXT)
    );

    BrotliOptions(Encoder.Parameters parameters) {
        this.parameters = ObjectUtil.checkNotNull(parameters, "Parameters");

        if (!Brotli.isAvailable()) {
            throw new IllegalStateException("Brotli is not available", Brotli.cause());
        }
    }

    public Encoder.Parameters parameters() {
        return parameters;
    }
}
