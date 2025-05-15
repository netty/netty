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
package io.netty.handler.codec.http.websocketx;

import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link WebSocketFrameMaskGenerator} implementation which returns a random int for masking.
 */
public final class RandomWebSocketFrameMaskGenerator implements WebSocketFrameMaskGenerator {
    public static final RandomWebSocketFrameMaskGenerator INSTANCE = new RandomWebSocketFrameMaskGenerator();

    private RandomWebSocketFrameMaskGenerator() {
    }

    @Override
    public int nextMask() {
        return ThreadLocalRandom.current().nextInt();
    }
}
