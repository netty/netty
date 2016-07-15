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

import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;


/**
 * {@link OpenSslSessionContext} implementation which offers extra methods which are only useful for the server-side.
 */
public final class OpenSslServerSessionContext extends OpenSslSessionContext {
    OpenSslServerSessionContext(ReferenceCountedOpenSslContext context) {
        super(context);
    }

    @Override
    public void setSessionTimeout(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException();
        }
        SSLContext.setSessionCacheTimeout(context.ctx, seconds);
    }

    @Override
    public int getSessionTimeout() {
        return (int) SSLContext.getSessionCacheTimeout(context.ctx);
    }

    @Override
    public void setSessionCacheSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        SSLContext.setSessionCacheSize(context.ctx, size);
    }

    @Override
    public int getSessionCacheSize() {
        return (int) SSLContext.getSessionCacheSize(context.ctx);
    }

    @Override
    public void setSessionCacheEnabled(boolean enabled) {
        long mode = enabled ? SSL.SSL_SESS_CACHE_SERVER : SSL.SSL_SESS_CACHE_OFF;
        SSLContext.setSessionCacheMode(context.ctx, mode);
    }

    @Override
    public boolean isSessionCacheEnabled() {
        return SSLContext.getSessionCacheMode(context.ctx) == SSL.SSL_SESS_CACHE_SERVER;
    }

    /**
     * Set the context within which session be reused (server side only)
     * See <a href="http://www.openssl.org/docs/ssl/SSL_CTX_set_session_id_context.html">
     *     man SSL_CTX_set_session_id_context</a>
     *
     * @param sidCtx can be any kind of binary data, it is therefore possible to use e.g. the name
     *               of the application and/or the hostname and/or service name
     * @return {@code true} if success, {@code false} otherwise.
     */
    public boolean setSessionIdContext(byte[] sidCtx) {
        return SSLContext.setSessionIdContext(context.ctx, sidCtx);
    }
}
