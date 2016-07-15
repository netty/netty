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

import io.netty.util.internal.ObjectUtil;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;
import org.apache.tomcat.jni.SessionTicketKey;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * OpenSSL specific {@link SSLSessionContext} implementation.
 */
public abstract class OpenSslSessionContext implements SSLSessionContext {
    private static final Enumeration<byte[]> EMPTY = new EmptyEnumeration();

    private final OpenSslSessionStats stats;
    final ReferenceCountedOpenSslContext context;

    // IMPORTANT: We take the OpenSslContext and not just the long (which points the native instance) to prevent
    //            the GC to collect OpenSslContext as this would also free the pointer and so could result in a
    //            segfault when the user calls any of the methods here that try to pass the pointer down to the native
    //            level.
    OpenSslSessionContext(ReferenceCountedOpenSslContext context) {
        this.context = context;
        stats = new OpenSslSessionStats(context);
    }

    @Override
    public SSLSession getSession(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        return null;
    }

    @Override
    public Enumeration<byte[]> getIds() {
        return EMPTY;
    }

    /**
     * Sets the SSL session ticket keys of this context.
     * @deprecated use {@link #setTicketKeys(OpenSslSessionTicketKey...)}.
     */
    @Deprecated
    public void setTicketKeys(byte[] keys) {
        ObjectUtil.checkNotNull(keys, "keys");
        SSLContext.clearOptions(context.ctx, SSL.SSL_OP_NO_TICKET);
        SSLContext.setSessionTicketKeys(context.ctx, keys);
    }

    /**
     * Sets the SSL session ticket keys of this context.
     */
    public void setTicketKeys(OpenSslSessionTicketKey... keys) {
        ObjectUtil.checkNotNull(keys, "keys");
        SSLContext.clearOptions(context.ctx, SSL.SSL_OP_NO_TICKET);
        SessionTicketKey[] ticketKeys = new SessionTicketKey[keys.length];
        for (int i = 0; i < ticketKeys.length; i++) {
            ticketKeys[i] = keys[i].key;
        }
        SSLContext.setSessionTicketKeys(context.ctx, ticketKeys);
    }

    /**
     * Enable or disable caching of SSL sessions.
     */
    public abstract void setSessionCacheEnabled(boolean enabled);

    /**
     * Return {@code true} if caching of SSL sessions is enabled, {@code false} otherwise.
     */
    public abstract boolean isSessionCacheEnabled();

    /**
     * Returns the stats of this context.
     */
    public OpenSslSessionStats stats() {
        return stats;
    }

    private static final class EmptyEnumeration implements Enumeration<byte[]> {
        @Override
        public boolean hasMoreElements() {
            return false;
        }

        @Override
        public byte[] nextElement() {
            throw new NoSuchElementException();
        }
    }
}
