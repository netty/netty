/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.quic;


import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class BoringSSLCertificateCallback {
    private static final byte[] BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n".getBytes(CharsetUtil.US_ASCII);
    private static final byte[] END_PRIVATE_KEY = "\n-----END PRIVATE KEY-----\n".getBytes(CharsetUtil.US_ASCII);

    /**
     * The types contained in the {@code keyTypeBytes} array.
     */
    // Extracted from https://github.com/openssl/openssl/blob/master/include/openssl/tls1.h
    private static final byte TLS_CT_RSA_SIGN = 1;
    private static final byte TLS_CT_DSS_SIGN = 2;
    private static final byte TLS_CT_RSA_FIXED_DH = 3;
    private static final byte TLS_CT_DSS_FIXED_DH = 4;
    private static final byte TLS_CT_ECDSA_SIGN = 64;
    private static final byte TLS_CT_RSA_FIXED_ECDH = 65;
    private static final byte TLS_CT_ECDSA_FIXED_ECDH = 66;

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
    private static final Map<String, String> DEFAULT_SERVER_KEY_TYPES = new HashMap<String, String>();
    static {
        DEFAULT_SERVER_KEY_TYPES.put("RSA", KEY_TYPE_RSA);
        DEFAULT_SERVER_KEY_TYPES.put("DHE_RSA", KEY_TYPE_RSA);
        DEFAULT_SERVER_KEY_TYPES.put("ECDHE_RSA", KEY_TYPE_RSA);
        DEFAULT_SERVER_KEY_TYPES.put("ECDHE_ECDSA", KEY_TYPE_EC);
        DEFAULT_SERVER_KEY_TYPES.put("ECDH_RSA", KEY_TYPE_EC_RSA);
        DEFAULT_SERVER_KEY_TYPES.put("ECDH_ECDSA", KEY_TYPE_EC_EC);
        DEFAULT_SERVER_KEY_TYPES.put("DH_RSA", KEY_TYPE_DH_RSA);
    }

    private static final Set<String> DEFAULT_CLIENT_KEY_TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(
            Arrays.asList(KEY_TYPE_RSA,
                    KEY_TYPE_DH_RSA,
                    KEY_TYPE_EC,
                    KEY_TYPE_EC_RSA,
                    KEY_TYPE_EC_EC)));

    // Directly returning this is safe as we never modify it within our JNI code.
    private static final long[] NO_KEY_MATERIAL_CLIENT_SIDE =  new long[] { 0, 0 };

    private final QuicheQuicSslEngineMap engineMap;
    private final X509ExtendedKeyManager keyManager;
    private final String password;
    private final Map<String, String> serverKeyTypes;
    private final Set<String> clientKeyTypes;

    BoringSSLCertificateCallback(QuicheQuicSslEngineMap engineMap,
                                 @Nullable X509ExtendedKeyManager keyManager,
                                 String password,
                                 Map<String, String> serverKeyTypes,
                                 Set<String> clientKeyTypes) {
        this.engineMap = engineMap;
        this.keyManager = keyManager;
        this.password = password;

        this.serverKeyTypes = serverKeyTypes != null ? serverKeyTypes : DEFAULT_SERVER_KEY_TYPES;
        this.clientKeyTypes = clientKeyTypes != null ? clientKeyTypes : DEFAULT_CLIENT_KEY_TYPES;
    }

    @SuppressWarnings("unused")
    long @Nullable [] handle(long ssl, byte[] keyTypeBytes, byte @Nullable [][] asn1DerEncodedPrincipals,
                             String[] authMethods) {
        QuicheQuicSslEngine engine = engineMap.get(ssl);
        if (engine == null) {
            return null;
        }

        try {
            if (keyManager == null) {
                if (engine.getUseClientMode()) {
                    return NO_KEY_MATERIAL_CLIENT_SIDE;
                }
                return null;
            }
            if (engine.getUseClientMode()) {
                final Set<String> keyTypesSet = supportedClientKeyTypes(keyTypeBytes);
                final String[] keyTypes = keyTypesSet.toArray(new String[0]);
                final X500Principal[] issuers;
                if (asn1DerEncodedPrincipals == null) {
                    issuers = null;
                } else {
                    issuers = new X500Principal[asn1DerEncodedPrincipals.length];
                    for (int i = 0; i < asn1DerEncodedPrincipals.length; i++) {
                        issuers[i] = new X500Principal(asn1DerEncodedPrincipals[i]);
                    }
                }
                return removeMappingIfNeeded(ssl, selectKeyMaterialClientSide(ssl, engine, keyTypes, issuers));
            } else {
                // For now we just ignore the asn1DerEncodedPrincipals as this is kind of inline with what the
                // OpenJDK SSLEngineImpl does.
                return removeMappingIfNeeded(ssl, selectKeyMaterialServerSide(ssl, engine, authMethods));
            }
        } catch (SSLException e) {
            engineMap.remove(ssl);
            return null;
        } catch (Throwable cause) {
            engineMap.remove(ssl);
            throw cause;
        }
    }

    private long @Nullable [] removeMappingIfNeeded(long ssl, long @Nullable [] result) {
        if (result == null) {
            engineMap.remove(ssl);
        }
        return result;
    }

    private long @Nullable [] selectKeyMaterialServerSide(long ssl, QuicheQuicSslEngine engine, String[] authMethods)
            throws SSLException {
        if (authMethods.length == 0) {
            throw new SSLHandshakeException("Unable to find key material");
        }

        // authMethods may contain duplicates or may result in the same type
        // but call chooseServerAlias(...) may be expensive. So let's ensure
        // we filter out duplicates.
        Set<String> typeSet = new HashSet<String>(serverKeyTypes.size());
        for (String authMethod : authMethods) {
            String type = serverKeyTypes.get(authMethod);
            if (type != null && typeSet.add(type)) {
                String alias = chooseServerAlias(engine, type);
                if (alias != null) {
                    return selectMaterial(ssl, engine, alias) ;
                }
            }
        }
        throw new SSLHandshakeException("Unable to find key material for auth method(s): "
                + Arrays.toString(authMethods));
    }

    private long @Nullable [] selectKeyMaterialClientSide(long ssl, QuicheQuicSslEngine engine, String[] keyTypes,
                                               X500Principal @Nullable [] issuer) {
        String alias = chooseClientAlias(engine, keyTypes, issuer);
        // Only try to set the keymaterial if we have a match. This is also consistent with what OpenJDK does:
        // https://hg.openjdk.java.net/jdk/jdk11/file/76072a077ee1/
        // src/java.base/share/classes/sun/security/ssl/CertificateRequest.java#l362
        if (alias != null) {
            return selectMaterial(ssl, engine, alias) ;
        }
        return NO_KEY_MATERIAL_CLIENT_SIDE;
    }

    private long @Nullable [] selectMaterial(long ssl, QuicheQuicSslEngine engine, String alias)  {
        X509Certificate[] certificates = keyManager.getCertificateChain(alias);
        if (certificates == null || certificates.length == 0) {
            return null;
        }
        byte[][] certs = new byte[certificates.length][];

        for (int i = 0; i < certificates.length; i++) {
            try {
                certs[i] = certificates[i].getEncoded();
            } catch (CertificateEncodingException e) {
                return null;
            }
        }

        final long key;
        PrivateKey privateKey = keyManager.getPrivateKey(alias);
        if (privateKey == BoringSSLKeylessPrivateKey.INSTANCE) {
            key = 0;
        } else {
            byte[] pemKey = toPemEncoded(privateKey);
            if (pemKey == null) {
                return null;
            }
            key = BoringSSL.EVP_PKEY_parse(pemKey, password);
        }
        long chain = BoringSSL.CRYPTO_BUFFER_stack_new(ssl, certs);
        engine.setLocalCertificateChain(certificates);

        // Return and signal that the key and chain should be released as well.
        return new long[] { key,  chain };
    }

    private static byte @Nullable [] toPemEncoded(PrivateKey key) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(BEGIN_PRIVATE_KEY);
            out.write(Base64.getEncoder().encode(key.getEncoded()));
            out.write(END_PRIVATE_KEY);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private String chooseClientAlias(QuicheQuicSslEngine engine,
                                     String[] keyTypes, X500Principal @Nullable [] issuer) {
        return keyManager.chooseEngineClientAlias(keyTypes, issuer, engine);
    }

    @Nullable
    private String chooseServerAlias(QuicheQuicSslEngine engine, String type) {
        return keyManager.chooseEngineServerAlias(type, null, engine);
    }

    /**
     * Gets the supported key types for client certificates.
     *
     * @param clientCertificateTypes {@code ClientCertificateType} values provided by the server.
     *        See https://www.ietf.org/assignments/tls-parameters/tls-parameters.xml.
     * @return supported key types that can be used in {@code X509KeyManager.chooseClientAlias} and
     *         {@code X509ExtendedKeyManager.chooseEngineClientAlias}.
     */
    private Set<String> supportedClientKeyTypes(byte @Nullable[] clientCertificateTypes) {
        if (clientCertificateTypes == null) {
            // Try all of the supported key types.
            return clientKeyTypes;
        }
        Set<String> result = new HashSet<>(clientCertificateTypes.length);
        for (byte keyTypeCode : clientCertificateTypes) {
            String keyType = clientKeyType(keyTypeCode);
            if (keyType == null) {
                // Unsupported client key type -- ignore
                continue;
            }
            result.add(keyType);
        }
        return result;
    }

    @Nullable
    private static String clientKeyType(byte clientCertificateType) {
        // See also https://www.ietf.org/assignments/tls-parameters/tls-parameters.xml
        switch (clientCertificateType) {
            case TLS_CT_RSA_SIGN:
                return KEY_TYPE_RSA; // RFC rsa_sign
            case TLS_CT_RSA_FIXED_DH:
                return KEY_TYPE_DH_RSA; // RFC rsa_fixed_dh
            case TLS_CT_ECDSA_SIGN:
                return KEY_TYPE_EC; // RFC ecdsa_sign
            case TLS_CT_RSA_FIXED_ECDH:
                return KEY_TYPE_EC_RSA; // RFC rsa_fixed_ecdh
            case TLS_CT_ECDSA_FIXED_ECDH:
                return KEY_TYPE_EC_EC; // RFC ecdsa_fixed_ecdh
            default:
                return null;
        }
    }
}
