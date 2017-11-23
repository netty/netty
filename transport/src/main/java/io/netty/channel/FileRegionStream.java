/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel;

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.UnstableApi;

/**
 * Provides a stream of {@link FileRegion}s.
 */
@UnstableApi
public interface FileRegionStream extends ReferenceCounted {

    /**
     * Returns a {@link FileRegion} that contains the number of bytes.
     */
    FileRegion read(long bytes);

    /**
     * Returns the number of bytes that are still avaiable via {@link #read(long)}.
     */
    long readableBytes();
}
