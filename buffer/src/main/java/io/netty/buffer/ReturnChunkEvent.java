/*
 * Copyright 2025 The Netty Project
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
package io.netty.buffer;

import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;

@SuppressWarnings("Since15")
@Label("Chunk Return")
@Name("ReturnChunkEvent")
@Description("Triggered when a memory chunk is prepared for re-use by an allocator")
final class ReturnChunkEvent extends AbstractChunkEvent {
    private static final FreeChunkEvent INSTANCE = new FreeChunkEvent();

    /**
     * Statically check if this event is enabled.
     */
    public static boolean isEventEnabled() {
        return INSTANCE.isEnabled();
    }

    @Description("Was this chunk returned to its previous magazine?")
    public boolean returnedToMagazine;
}
