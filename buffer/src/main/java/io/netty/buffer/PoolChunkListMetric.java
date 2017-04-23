/*
 * Copyright 2015 The Netty Project
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
package io.netty.buffer;

/**
 * Metrics for a list of chunks.
 */
public interface PoolChunkListMetric extends Iterable<PoolChunkMetric> {

    /**
     * Return the minimum usage of the chunk list before which chunks are promoted to the previous list.
     */
    int minUsage();

    /**
     * Return the maximum usage of the chunk list after which chunks are promoted to the next list.
     */
    int maxUsage();
}
