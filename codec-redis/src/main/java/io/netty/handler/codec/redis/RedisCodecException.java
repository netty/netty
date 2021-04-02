/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.redis;

import io.netty.handler.codec.CodecException;
import io.netty.util.internal.UnstableApi;

/**
 * An {@link Exception} which is thrown by {@link RedisEncoder} or {@link RedisDecoder}.
 */
@UnstableApi
public final class RedisCodecException extends CodecException {

    private static final long serialVersionUID = 5570454251549268063L;

    /**
     * Creates a new instance.
     */
    public RedisCodecException(String message) {
        super(message);
    }

    /**
     * Creates a new instance.
     */
    public RedisCodecException(Throwable cause) {
        super(cause);
    }
}
