/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

import java.util.Collections;
import java.util.Map;

/**
 * A WebSocket Extension data from the <tt>Sec-WebSocket-Extensions</tt> header.
 *
 * See <tt>io.netty.handler.codec.http.HttpHeaders.Names.SEC_WEBSOCKET_EXTENSIONS</tt>.
 */
public final class WebSocketExtensionData {

    private final String name;
    private final Map<String, String> parameters;

    public WebSocketExtensionData(String name, Map<String, String> parameters) {
        this.name = ObjectUtil.checkNotNull(name, "name");
        this.parameters = Collections.unmodifiableMap(
                ObjectUtil.checkNotNull(parameters, "parameters"));
    }

    /**
     * @return the extension name.
     */
    public String name() {
        return name;
    }

    /**
     * @return the extension optional parameters.
     */
    public Map<String, String> parameters() {
        return parameters;
    }
}
