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

import org.apache.tomcat.jni.SSLContext;

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
    final long context;

    OpenSslSessionContext(long context) {
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
     */
    public void setTicketKeys(byte[] keys) {
        if (keys == null) {
            throw new NullPointerException("keys");
        }
        SSLContext.setSessionTicketKeys(context, keys);
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
