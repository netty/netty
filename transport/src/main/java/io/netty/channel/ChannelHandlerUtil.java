/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.Freeable;

/**
 * Utilities for {@link ChannelHandler} implementations.
 */
public final class ChannelHandlerUtil {

    /**
     * Try to free up resources that are held by the message.
     */
    public static void freeMessage(Object msg) throws Exception {
        if (msg instanceof Freeable) {
            try {
                ((Freeable) msg).free();
            } catch (UnsupportedOperationException e) {
                // This can happen for derived buffers
                // TODO: Think about this
            }
        }
    }

    private ChannelHandlerUtil() {
        // Unused
    }
}
