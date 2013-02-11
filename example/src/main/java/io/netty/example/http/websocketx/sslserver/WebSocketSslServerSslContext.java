/*
 * Copyright 2012 The Netty Project
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
package io.netty.example.http.websocketx.sslserver;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.Security;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates a {@link SSLContext} for just server certificates.
 */
public final class WebSocketSslServerSslContext {

    private static final Logger logger = Logger.getLogger(WebSocketSslServerSslContext.class.getName());
    private static final String PROTOCOL = "TLS";
    private final SSLContext _serverContext;

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
    private interface SingletonHolder {
        WebSocketSslServerSslContext INSTANCE = new WebSocketSslServerSslContext();
    }

    /**
     * Constructor for singleton
     */
    private WebSocketSslServerSslContext() {
        SSLContext serverContext = null;
        try {
            // Key store (Server side certificate)
            String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
            if (algorithm == null) {
                algorithm = "SunX509";
            }

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
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error initializing SslContextManager.", ex);
            System.exit(1);
        } finally {
            _serverContext = serverContext;
        }
    }

    /**
     * Returns the server context with server side key store
     */
    public SSLContext serverContext() {
        return _serverContext;
    }

}
