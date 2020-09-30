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

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * Manages key material for {@link OpenSslEngine}s and so set the right {@link PrivateKey}s and
 * {@link X509Certificate}s.
 */
final class OpenSslKeyMaterialManager {

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

    private final OpenSslKeyMaterialProvider provider;

    OpenSslKeyMaterialManager(OpenSslKeyMaterialProvider provider) {
        this.provider = provider;
    }

    void setKeyMaterialServerSide(ReferenceCountedOpenSslEngine engine) throws SSLException {
        String[] authMethods = engine.authMethods();
        if (authMethods.length == 0) {
            throw new SSLHandshakeException("Unable to find key material");
        }

        // authMethods may contain duplicates or may result in the same type
        // but call chooseServerAlias(...) may be expensive. So let's ensure
        // we filter out duplicates.
        Set<String> typeSet = new HashSet<String>(KEY_TYPES.size());
        for (String authMethod : authMethods) {
            String type = KEY_TYPES.get(authMethod);
            if (type != null && typeSet.add(type)) {
                String alias = chooseServerAlias(engine, type);
                if (alias != null) {
                    // We found a match... let's set the key material and return.
                    setKeyMaterial(engine, alias);
                    return;
                }
            }
        }
        throw new SSLHandshakeException("Unable to find key material for auth method(s): "
                + Arrays.toString(authMethods));
    }

    void setKeyMaterialClientSide(ReferenceCountedOpenSslEngine engine, String[] keyTypes,
                                  X500Principal[] issuer) throws SSLException {
        String alias = chooseClientAlias(engine, keyTypes, issuer);
        // Only try to set the keymaterial if we have a match. This is also consistent with what OpenJDK does:
        // http://hg.openjdk.java.net/jdk/jdk11/file/76072a077ee1/
        // src/java.base/share/classes/sun/security/ssl/CertificateRequest.java#l362
        if (alias != null) {
            setKeyMaterial(engine, alias);
        }
    }

    private void setKeyMaterial(ReferenceCountedOpenSslEngine engine, String alias) throws SSLException {
        OpenSslKeyMaterial keyMaterial = null;
        try {
            keyMaterial = provider.chooseKeyMaterial(engine.alloc, alias);
            if (keyMaterial == null) {
                return;
            }
            engine.setKeyMaterial(keyMaterial);
        } catch (SSLException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLException(e);
        } finally {
            if (keyMaterial != null) {
                keyMaterial.release();
            }
        }
    }
    private String chooseClientAlias(ReferenceCountedOpenSslEngine engine,
                                       String[] keyTypes, X500Principal[] issuer) {
        X509KeyManager manager = provider.keyManager();
        if (manager instanceof X509ExtendedKeyManager) {
            return ((X509ExtendedKeyManager) manager).chooseEngineClientAlias(keyTypes, issuer, engine);
        }
        return manager.chooseClientAlias(keyTypes, issuer, null);
    }

    private String chooseServerAlias(ReferenceCountedOpenSslEngine engine, String type) {
        X509KeyManager manager = provider.keyManager();
        if (manager instanceof X509ExtendedKeyManager) {
            return ((X509ExtendedKeyManager) manager).chooseEngineServerAlias(type, null, engine);
        }
        return manager.chooseServerAlias(type, null, null);
    }
}
