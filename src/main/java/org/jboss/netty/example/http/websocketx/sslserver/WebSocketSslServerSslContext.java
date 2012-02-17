/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.http.websocketx.sslserver;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.Security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Creates a {@link SSLContext} for just server certificates.
 */
public final class WebSocketSslServerSslContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketSslServerSslContext.class);
    private static final String PROTOCOL = "TLS";
    private SSLContext _serverContext;

    /**
     * Returns the singleton instance for this class
     */
    public static WebSocketSslServerSslContext getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * SingletonHolder is loaded on the first execution of Singleton.getInstance() or the first access to
     * SingletonHolder.INSTANCE, not before.
     * 
     * See http://en.wikipedia.org/wiki/Singleton_pattern
     */
    private static class SingletonHolder {

        public static final WebSocketSslServerSslContext INSTANCE = new WebSocketSslServerSslContext();
    }

    /**
     * Constructor for singleton
     */
    private WebSocketSslServerSslContext() {
        try {
            // Key store (Server side certificate)
            String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
            if (algorithm == null) {
                algorithm = "SunX509";
            }

            SSLContext serverContext = null;
            try {
                String keyStoreFilePath = System.getProperty("keystore.file.path");
                String keyStoreFilePassword = System.getProperty("keystore.file.password");

                KeyStore ks = KeyStore.getInstance("JKS");
                FileInputStream fin = new FileInputStream(keyStoreFilePath);
                ks.load(fin, keyStoreFilePassword.toCharArray());

                // Set up key manager factory to use our key store
                // Assume key password is the same as the key store file
                // password
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
                kmf.init(ks, keyStoreFilePassword.toCharArray());

                // Initialise the SSLContext to work with our key managers.
                serverContext = SSLContext.getInstance(PROTOCOL);
                serverContext.init(kmf.getKeyManagers(), null, null);
            } catch (Exception e) {
                throw new Error("Failed to initialize the server-side SSLContext", e);
            }
            _serverContext = serverContext;
        } catch (Exception ex) {
            if (logger.isErrorEnabled()) {
                logger.error("Error initializing SslContextManager. " + ex.getMessage(), ex);
            }
            System.exit(1);

        }
    }

    /**
     * Returns the server context with server side key store
     */
    public SSLContext getServerContext() {
        return _serverContext;
    }
}
