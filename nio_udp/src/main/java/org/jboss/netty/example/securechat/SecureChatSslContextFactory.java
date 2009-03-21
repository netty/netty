/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.example.securechat;

import java.security.KeyStore;
import java.security.Security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Creates a bogus {@link SSLContext}.  A client-side context created by this
 * factory accepts any certificate even if it is invalid.  A server-side context
 * created by this factory sends a bogus certificate defined in {@link SecureChatKeyStore}.
 *
 * You will have to create your context differently in a real world application.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class SecureChatSslContextFactory {

    private static final String PROTOCOL = "TLS";
    private static final SSLContext SERVER_CONTEXT;
    private static final SSLContext CLIENT_CONTEXT;

    static {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }

        SSLContext serverContext = null;
        SSLContext clientContext = null;
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(SecureChatKeyStore.asInputStream(),
                    SecureChatKeyStore.getKeyStorePassword());

            // Set up key manager factory to use our key store
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
            kmf.init(ks, SecureChatKeyStore.getCertificatePassword());

            // Initialize the SSLContext to work with our key managers.
            serverContext = SSLContext.getInstance(PROTOCOL);
            serverContext.init(
                    kmf.getKeyManagers(),
                    SecureChatTrustManagerFactory.getTrustManagers(), null);
        } catch (Exception e) {
            throw new Error(
                    "Failed to initialize the server-side SSLContext", e);
        }

        try {
            clientContext = SSLContext.getInstance(PROTOCOL);
            clientContext.init(
                    null, SecureChatTrustManagerFactory.getTrustManagers(), null);
        } catch (Exception e) {
            throw new Error(
                    "Failed to initialize the client-side SSLContext", e);
        }

        SERVER_CONTEXT = serverContext;
        CLIENT_CONTEXT = clientContext;
    }

    public static SSLContext getServerContext() {
        return SERVER_CONTEXT;
    }

    public static SSLContext getClientContext() {
        return CLIENT_CONTEXT;
    }
}
