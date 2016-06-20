/*
 * Copyright 2016 The Netty Project
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

import javax.net.ssl.SSLException;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages key material for {@link OpenSslEngine}s and so set the right {@link PrivateKey}s and
 * {@link X509Certificate}s.
 */
class OpenSslKeyMaterialManager {

    // Code in this class is inspired by code of conscrypts:
    // - https://android.googlesource.com/platform/external/
    //   conscrypt/+/master/src/main/java/org/conscrypt/OpenSSLEngineImpl.java
    // - https://android.googlesource.com/platform/external/
    //   conscrypt/+/master/src/main/java/org/conscrypt/SSLParametersImpl.java
    //
    static final String KEY_TYPE_RSA = "RSA";
    static final String KEY_TYPE_DH_RSA = "DH_RSA";
    static final String KEY_TYPE_EC = "EC";
    static final String KEY_TYPE_EC_EC = "EC_EC";
    static final String KEY_TYPE_EC_RSA = "EC_RSA";

    // key type mappings for types.
    private static final Map<String, String> KEY_TYPES = new HashMap<String, String>();
    static {
        KEY_TYPES.put("RSA", KEY_TYPE_RSA);
        KEY_TYPES.put("DHE_RSA", KEY_TYPE_RSA);
        KEY_TYPES.put("ECDHE_RSA", KEY_TYPE_RSA);
        KEY_TYPES.put("ECDHE_ECDSA", KEY_TYPE_EC);
        KEY_TYPES.put("ECDH_RSA", KEY_TYPE_EC_RSA);
        KEY_TYPES.put("ECDH_ECDSA", KEY_TYPE_EC_EC);
        KEY_TYPES.put("DH_RSA", KEY_TYPE_DH_RSA);
    }

    private final X509KeyManager keyManager;
    private final String password;

    OpenSslKeyMaterialManager(X509KeyManager keyManager, String password) {
        this.keyManager = keyManager;
        this.password = password;
    }

    void setKeyMaterial(OpenSslEngine engine) throws SSLException {
        long ssl = engine.sslPointer();
        String[] authMethods = SSL.authenticationMethods(ssl);
        for (String authMethod : authMethods) {
            String type = KEY_TYPES.get(authMethod);
            if (type != null) {
                setKeyMaterial(ssl, chooseServerAlias(engine, type));
            }
        }
    }

    void setKeyMaterial(OpenSslEngine engine, String[] keyTypes, X500Principal[] issuer) throws SSLException {
        setKeyMaterial(engine.sslPointer(), chooseClientAlias(engine, keyTypes, issuer));
    }

    private void setKeyMaterial(long ssl, String alias) throws SSLException {
        long keyBio = 0;
        long keyCertChainBio = 0;
        try {
            // TODO: Should we cache these and so not need to do a memory copy all the time ?
            PrivateKey key = keyManager.getPrivateKey(alias);
            X509Certificate[] certificates = keyManager.getCertificateChain(alias);

            if (certificates != null && certificates.length != 0) {
                keyCertChainBio = OpenSslContext.toBIO(keyManager.getCertificateChain(alias));
                if (key != null) {
                    keyBio = OpenSslContext.toBIO(key);
                }
                SSL.setCertificateBio(ssl, keyCertChainBio, keyBio, password);

                // We may have more then one cert in the chain so add all of them now.
                SSL.setCertificateChainBio(ssl, keyCertChainBio, false);
            }
        } catch (SSLException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLException(e);
        } finally {
            if (keyBio != 0) {
                SSL.freeBIO(keyBio);
            }
            if (keyCertChainBio != 0) {
                SSL.freeBIO(keyCertChainBio);
            }
        }
    }

    protected String chooseClientAlias(@SuppressWarnings("unused") OpenSslEngine engine,
                                       String[] keyTypes, X500Principal[] issuer) {
        return keyManager.chooseClientAlias(keyTypes, issuer, null);
    }

    protected String chooseServerAlias(@SuppressWarnings("unused") OpenSslEngine engine, String type) {
        return keyManager.chooseServerAlias(type, null, null);
    }
}
