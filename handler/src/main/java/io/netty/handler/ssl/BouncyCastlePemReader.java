/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.handler.ssl.util.BouncyCastleUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.PrivateKey;

final class BouncyCastlePemReader {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BouncyCastlePemReader.class);

    /**
     * Generates a new {@link PrivateKey}.
     *
     * @param keyInputStream an input stream for a PKCS#1 or PKCS#8 private key in PEM format.
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not password-protected.
     * @return generated {@link PrivateKey}.
     */
    public static PrivateKey getPrivateKey(InputStream keyInputStream, String keyPassword) {
        if (!BouncyCastleUtil.isBcPkixAvailable()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Bouncy castle provider is unavailable.", BouncyCastleUtil.unavailabilityCauseBcPkix());
            }
            return null;
        }
        try {
            PEMParser parser = newParser(keyInputStream);
            return getPrivateKey(parser, keyPassword);
        } catch (Exception e) {
            logger.debug("Unable to extract private key", e);
            return null;
        }
    }

    /**
     * Generates a new {@link PrivateKey}.
     *
     * @param keyFile a PKCS#1 or PKCS#8 private key file in PEM format.
     * @param keyPassword the password of the {@code keyFile}.
     *                    {@code null} if it's not pa ssword-protected.
     * @return generated {@link PrivateKey}.
     */
    public static PrivateKey getPrivateKey(File keyFile, String keyPassword) {
        if (!BouncyCastleUtil.isBcPkixAvailable()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Bouncy castle provider is unavailable.", BouncyCastleUtil.unavailabilityCauseBcPkix());
            }
            return null;
        }
        try {
            PEMParser parser = newParser(keyFile);
            return getPrivateKey(parser, keyPassword);
        } catch (Exception e) {
            logger.debug("Unable to extract private key", e);
            return null;
        }
    }

    private static JcaPEMKeyConverter newConverter() {
        return new JcaPEMKeyConverter().setProvider(BouncyCastleUtil.getBcProviderJce());
    }

    private static PrivateKey getPrivateKey(PEMParser pemParser, String keyPassword) throws IOException,
            PKCSException, OperatorCreationException {
        try {
            JcaPEMKeyConverter converter = newConverter();
            PrivateKey pk = null;

            Object object = pemParser.readObject();
            while (object != null && pk == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Parsed PEM object of type {} and assume " +
                                 "key is {}encrypted", object.getClass().getName(), keyPassword == null? "not " : "");
                }

                if (keyPassword == null) {
                    // assume private key is not encrypted
                    if (object instanceof PrivateKeyInfo) {
                        pk = converter.getPrivateKey((PrivateKeyInfo) object);
                    } else if (object instanceof PEMKeyPair) {
                        pk = converter.getKeyPair((PEMKeyPair) object).getPrivate();
                    } else {
                        logger.debug("Unable to handle PEM object of type {} as a non encrypted key",
                                     object.getClass());
                    }
                } else {
                    // assume private key is encrypted
                    if (object instanceof PEMEncryptedKeyPair) {
                        PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder()
                                .setProvider(BouncyCastleUtil.getBcProviderJce())
                                .build(keyPassword.toCharArray());
                        pk = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv)).getPrivate();
                    } else if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
                        InputDecryptorProvider pkcs8InputDecryptorProvider =
                                new JceOpenSSLPKCS8DecryptorProviderBuilder()
                                        .setProvider(BouncyCastleUtil.getBcProviderJce())
                                        .build(keyPassword.toCharArray());
                        pk = converter.getPrivateKey(((PKCS8EncryptedPrivateKeyInfo) object)
                                                             .decryptPrivateKeyInfo(pkcs8InputDecryptorProvider));
                    } else {
                        logger.debug("Unable to handle PEM object of type {} as a encrypted key", object.getClass());
                    }
                }

                // Try reading next entry in the pem file if private key is not yet found
                if (pk == null) {
                    object = pemParser.readObject();
                }
            }

            if (pk == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("No key found");
                }
            }

            return pk;
        } finally {
            if (pemParser != null) {
                try {
                    pemParser.close();
                } catch (Exception exception) {
                    logger.debug("Failed closing pem parser", exception);
                }
            }
        }
    }

    private static PEMParser newParser(File keyFile) throws FileNotFoundException {
        return new PEMParser(new FileReader(keyFile));
    }

    private static PEMParser newParser(InputStream keyInputStream) {
        return new PEMParser(new InputStreamReader(keyInputStream, CharsetUtil.US_ASCII));
    }

    private BouncyCastlePemReader() { }
}
