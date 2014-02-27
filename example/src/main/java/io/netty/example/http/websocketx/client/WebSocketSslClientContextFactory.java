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
package io.netty.example.http.websocketx.client;

import javax.net.ssl.SSLContext;

/**
 * Creates a bogus {@link javax.net.ssl.SSLContext}.  A client-side context created by this
 * factory accepts any certificate even if it is invalid.
 * <p>
 * You will have to create your context differently in a real world application.
 * <p>
 * Modified from {@link io.netty.example.securechat.SecureChatSslContextFactory}
 */
public final class WebSocketSslClientContextFactory {

    private static final String PROTOCOL = "TLS";
    private static final SSLContext CONTEXT;

    static {
        SSLContext clientContext;
        try {
            clientContext = SSLContext.getInstance(PROTOCOL);
            clientContext.init(null, WebSocketSslClientTrustManagerFactory.getTrustManagers(), null);
        } catch (Exception e) {
            throw new Error(
                    "Failed to initialize the client-side SSLContext", e);
        }

        CONTEXT = clientContext;
    }

    public static SSLContext getContext() {
        return CONTEXT;
    }

    private WebSocketSslClientContextFactory() {
        // Unused
    }
}
