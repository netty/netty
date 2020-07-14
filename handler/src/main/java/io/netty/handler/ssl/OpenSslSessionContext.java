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

import io.netty.internal.tcnative.SSL;
import io.netty.internal.tcnative.SSLContext;
import io.netty.internal.tcnative.SessionTicketKey;
import io.netty.util.internal.ObjectUtil;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.locks.Lock;

/**
 * OpenSSL specific {@link SSLSessionContext} implementation.
 */
public abstract class OpenSslSessionContext implements SSLSessionContext {

    private final OpenSslSessionStats stats;

    // The OpenSslKeyMaterialProvider is not really used by the OpenSslSessionContext but only be stored here
    // to make it easier to destroy it later because the ReferenceCountedOpenSslContext will hold a reference
    // to OpenSslSessionContext.
    private final OpenSslKeyMaterialProvider provider;

    final ReferenceCountedOpenSslContext context;
    final OpenSslNullSession nullSession;

    final OpenSslSessionCache sessionCache;
    private final long mask;

    // IMPORTANT: We take the OpenSslContext and not just the long (which points the native instance) to prevent
    //            the GC to collect OpenSslContext as this would also free the pointer and so could result in a
    //            segfault when the user calls any of the methods here that try to pass the pointer down to the native
    //            level.
    OpenSslSessionContext(ReferenceCountedOpenSslContext context, OpenSslKeyMaterialProvider provider, long mask,
                          OpenSslSessionCache cache) {
        this.context = context;
        this.provider = provider;
        this.mask = mask;
        stats = new OpenSslSessionStats(context);
        sessionCache = cache;
        // If we do not use the KeyManagerFactory we need to set localCertificateChain now.
        // When we use a KeyManagerFactory it will be set during setKeyMaterial(...).
        nullSession = new OpenSslNullSession(this, provider == null ? context.keyCertChain : null);
        SSLContext.setSSLSessionCache(context.ctx, cache);
    }

    final DefaultOpenSslSession newOpenSslSession(long sslSession, String peerHost,
                                            int peerPort, String protocol, String cipher,
                                            OpenSslJavaxX509Certificate[] peerCertificateChain, long creationTime) {
        return sessionCache.newOpenSslSession(sslSession, this, peerHost, peerPort, protocol, cipher,
                peerCertificateChain, creationTime);
    }

    @Override
    public void setSessionCacheSize(int size) {
        ObjectUtil.checkPositiveOrZero(size, "size");
        sessionCache.setSessionCacheSize(size);
    }

    @Override
    public int getSessionCacheSize() {
        return sessionCache.getSessionCacheSize();
    }

    @Override
    public void setSessionTimeout(int seconds) {
        ObjectUtil.checkPositiveOrZero(seconds, "seconds");

        Lock writerLock = context.ctxLock.writeLock();
        writerLock.lock();
        try {
            SSLContext.setSessionCacheTimeout(context.ctx, seconds);
            sessionCache.setSessionTimeout(seconds);
        } finally {
            writerLock.unlock();
        }
    }

    @Override
    public int getSessionTimeout() {
        return sessionCache.getSessionTimeout();
    }

    @Override
    public SSLSession getSession(byte[] bytes) {
        return sessionCache.getSession(bytes);
    }

    @Override
    public Enumeration<byte[]> getIds() {
        return Collections.enumeration(sessionCache.getIds());
    }

    /**
     * Sets the SSL session ticket keys of this context.
     * @deprecated use {@link #setTicketKeys(OpenSslSessionTicketKey...)}.
     */
    @Deprecated
    public void setTicketKeys(byte[] keys) {
        if (keys.length % SessionTicketKey.TICKET_KEY_SIZE != 0) {
            throw new IllegalArgumentException("keys.length % " + SessionTicketKey.TICKET_KEY_SIZE  + " != 0");
        }
        SessionTicketKey[] tickets = new SessionTicketKey[keys.length / SessionTicketKey.TICKET_KEY_SIZE];
        for (int i = 0, a = 0; i < tickets.length; i++) {
            byte[] name = Arrays.copyOfRange(keys, a, SessionTicketKey.NAME_SIZE);
            a += SessionTicketKey.NAME_SIZE;
            byte[] hmacKey = Arrays.copyOfRange(keys, a, SessionTicketKey.HMAC_KEY_SIZE);
            i += SessionTicketKey.HMAC_KEY_SIZE;
            byte[] aesKey = Arrays.copyOfRange(keys, a, SessionTicketKey.AES_KEY_SIZE);
            a += SessionTicketKey.AES_KEY_SIZE;
            tickets[i] = new SessionTicketKey(name, hmacKey, aesKey);
        }
        Lock writerLock = context.ctxLock.writeLock();
        writerLock.lock();
        try {
            SSLContext.clearOptions(context.ctx, SSL.SSL_OP_NO_TICKET);
            SSLContext.setSessionTicketKeys(context.ctx, tickets);
        } finally {
            writerLock.unlock();
        }
    }

    /**
     * Sets the SSL session ticket keys of this context. Depending on the underlying native library you may omit the
     * argument or pass an empty array and so let the native library handle the key generation and rotating for you.
     * If this is supported by the underlying native library should be checked in this case. For example
     * <a href="https://commondatastorage.googleapis.com/chromium-boringssl-docs/ssl.h.html#Session-tickets/">
     *     BoringSSL</a> is known to support this.
     */
    public void setTicketKeys(OpenSslSessionTicketKey... keys) {
        ObjectUtil.checkNotNull(keys, "keys");
        SessionTicketKey[] ticketKeys = new SessionTicketKey[keys.length];
        for (int i = 0; i < ticketKeys.length; i++) {
            ticketKeys[i] = keys[i].key;
        }
        Lock writerLock = context.ctxLock.writeLock();
        writerLock.lock();
        try {
            SSLContext.clearOptions(context.ctx, SSL.SSL_OP_NO_TICKET);
            if (ticketKeys.length > 0) {
                SSLContext.setSessionTicketKeys(context.ctx, ticketKeys);
            }
        } finally {
            writerLock.unlock();
        }
    }

    /**
     * Enable or disable caching of SSL sessions.
     */
    public void setSessionCacheEnabled(boolean enabled) {
        long mode = enabled ? mask | SSL.SSL_SESS_CACHE_NO_INTERNAL_LOOKUP |
                SSL.SSL_SESS_CACHE_NO_INTERNAL_STORE : SSL.SSL_SESS_CACHE_OFF;
        Lock writerLock = context.ctxLock.writeLock();
        writerLock.lock();
        try {
            SSLContext.setSessionCacheMode(context.ctx, mode);
            if (!enabled) {
                sessionCache.freeSessions();
            }
        } finally {
            writerLock.unlock();
        }
    }

    /**
     * Return {@code true} if caching of SSL sessions is enabled, {@code false} otherwise.
     */
    public boolean isSessionCacheEnabled() {
        Lock readerLock = context.ctxLock.readLock();
        readerLock.lock();
        try {
            return (SSLContext.getSessionCacheMode(context.ctx) & mask) != 0;
        } finally {
            readerLock.unlock();
        }
    }

    /**
     * Returns the stats of this context.
     */
    public OpenSslSessionStats stats() {
        return stats;
    }

    final void destroy() {
        if (provider != null) {
            provider.destroy();
        }
        sessionCache.freeSessions();
    }
}
