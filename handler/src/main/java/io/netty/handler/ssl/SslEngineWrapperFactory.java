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
package io.netty.handler.ssl;

import java.util.List;

import javax.net.ssl.SSLEngine;

/**
 * Abstract factory pattern for wrapping an {@link SSLEngine} object.
 * This is useful for NPN/APLN support.
 */
public interface SslEngineWrapperFactory {
    /**
     * Abstract factory pattern for wrapping an {@link SSLEngine} object.
     * This is useful for NPN/APLN support.
     *
     * @param engine The engine to wrap
     * @param protocols The application level protocols that are supported
     * @param isServer
     * <ul>
     * <li>{@code true} if the engine is for server side of connections</li>
     * <li>{@code false} if the engine is for client side of connections</li>
     * </ul>
     * @return The resulting wrapped engine. This may just be {@code engine}
     */
    SSLEngine wrapSslEngine(SSLEngine engine, List<String> protocols, boolean isServer);
}
