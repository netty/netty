/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.memcache;

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.UnstableApi;

/**
 * Marker interface for both ascii and binary messages.
 */
@UnstableApi
public interface MemcacheMessage extends MemcacheObject, ReferenceCounted {

    /**
     * Increases the reference count by {@code 1}.
     */
    @Override
    MemcacheMessage retain();

    /**
     * Increases the reference count by the specified {@code increment}.
     */
    @Override
    MemcacheMessage retain(int increment);

    @Override
    MemcacheMessage touch();

    @Override
    MemcacheMessage touch(Object hint);
}
