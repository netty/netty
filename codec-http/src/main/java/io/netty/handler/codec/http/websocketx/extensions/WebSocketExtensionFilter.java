/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * Filter that is responsible to skip the evaluation of a certain extension
 * according to standard.
 */
public interface WebSocketExtensionFilter {

    /**
     * A {@link WebSocketExtensionFilter} that never skip the evaluation of an
     * any given extensions {@link WebSocketExtension}.
     */
    WebSocketExtensionFilter NEVER_SKIP = new WebSocketExtensionFilter() {
        @Override
        public boolean mustSkip(WebSocketFrame frame) {
            return false;
        }
    };

    /**
     * A {@link WebSocketExtensionFilter} that always skip the evaluation of an
     * any given extensions {@link WebSocketExtension}.
     */
    WebSocketExtensionFilter ALWAYS_SKIP = new WebSocketExtensionFilter() {
        @Override
        public boolean mustSkip(WebSocketFrame frame) {
            return true;
        }
    };

    /**
     * Returns {@code true} if the evaluation of the extension must skipped
     * for the given frame otherwise {@code false}.
     */
    boolean mustSkip(WebSocketFrame frame);

}
