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

/**
 * Allows to customize how the mask is generated that is used to mask the {@link WebSocketFrame}. Masking
 * is only be done on the client-side.
 */
public interface WebSocketFrameMaskGenerator {

    /**
     * Return the next mask that is used to mask the payload of the {@link WebSocketFrame}.
     *
     * @return  mask.
     */
    int nextMask();
}
