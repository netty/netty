/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions;

/**
 * Extension filter provider that is responsible to provide filters for a certain {@link WebSocketExtension} extension.
 */
public interface WebSocketExtensionFilterProvider {

    WebSocketExtensionFilterProvider DEFAULT = new WebSocketExtensionFilterProvider() {
        @Override
        public WebSocketExtensionFilter encoderFilter() {
            return WebSocketExtensionFilter.NEVER_SKIP;
        }

        @Override
        public WebSocketExtensionFilter decoderFilter() {
            return WebSocketExtensionFilter.NEVER_SKIP;
        }
    };

    /**
     * Returns the extension filter for {@link WebSocketExtensionEncoder} encoder.
     */
    WebSocketExtensionFilter encoderFilter();

    /**
     * Returns the extension filter for {@link WebSocketExtensionDecoder} decoder.
     */
    WebSocketExtensionFilter decoderFilter();

}
