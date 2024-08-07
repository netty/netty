/*
 * Copyright 2021 The Netty Project
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

import io.netty.util.internal.UnstableApi;

/**
 * NULL of <a href="https://github.com/antirez/RESP3/blob/master/spec.md">RESP3</a>.
 */
@UnstableApi
public final class NullRedisMessage implements RedisMessage {

    public static final NullRedisMessage INSTANCE = new NullRedisMessage();

    private NullRedisMessage() {
    }
}
