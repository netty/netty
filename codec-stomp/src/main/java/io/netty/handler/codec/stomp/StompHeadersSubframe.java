/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

/**
 * An interface that defines a {@link StompFrame}'s command and headers.
 *
 * @see StompCommand
 * @see StompHeaders
 */
public interface StompHeadersSubframe extends StompSubframe {
    /**
     * Returns command of this frame.
     */
    StompCommand command();

    /**
     * Returns headers of this frame.
     */
    StompHeaders headers();
}
