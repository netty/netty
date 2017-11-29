/*
 * Copyright 2017 The Netty Project
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

package io.netty.handler.ssl.util;

import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.Security;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;

public final class BouncyCastlePemReader {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BouncyCastlePemReader.class);
    private static Boolean available;

    public static boolean isBcAvailable() {
        tryLoadingBc();
        return available;
    }

    private static void tryLoadingBc() {
        if (available != null) {
            return;
        }

        available = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
                try {
                    Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider", true, this.getClass()
                            .getClassLoader());
                    if (Security.getProvider("BC") == null) {
                        Security.addProvider(new BouncyCastleProvider());
                    }
                    logger.debug("Bouncy Castle provider available");
                    return Boolean.TRUE;
                } catch (Exception e) {
                    logger.debug("Cannot load Bouncy Castle provider");
                    return Boolean.FALSE;
                }
            }
        });
    }

    static {
        isBcAvailable();
    }

    public static PrivateKey getPrivateKey(InputStream keyInputStream, String keyPassword) {
        try {
            return getPrivateKey(getParser(keyInputStream), keyPassword);
        } catch (Exception e) {
            logger.debug("Unable to extract private key due to " + e);
            return null;
        }
    }

    public static PrivateKey getPrivateKey(File keyFile, String keyPassword) {
        try {
            return getPrivateKey(getParser(keyFile), keyPassword);
        } catch (Exception e) {
            logger.debug("Unable to extract private key due to " + e);
            return null;
        }
    }

    private static PrivateKey getPrivateKey(PEMParser pemParser, String keyPassword) throws PEMException, IOException,
            PKCSException, OperatorCreationException {

        try {
            Object object = pemParser.readObject();
            logger.debug("Parsed PEM object of type " + object.getClass().getName() + " and assume key is "
                    + (keyPassword == null ? "not " : "") + "encrypted");
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            PrivateKey pk = null;

            if (keyPassword == null) {
                // assume private key is not encrypted
                if (object instanceof PrivateKeyInfo) {
                    pk = converter.getPrivateKey((PrivateKeyInfo) object);
                } else if (object instanceof PEMKeyPair) {
                    pk = converter.getKeyPair((PEMKeyPair) object).getPrivate();
                } else {
                    logger.debug("Unable to handle PEM object of type "
                                   + object.getClass() + " as a non encrypted key");
                }
            } else {
                // assume private key is encrypted
                if (object instanceof PEMEncryptedKeyPair) {
                    PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder()
                            .build(keyPassword.toCharArray());
                    pk = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv)).getPrivate();
                } else if (object instanceof PKCS8EncryptedPrivateKeyInfo) {
                    InputDecryptorProvider pkcs8InputDecryptorProvider = new JceOpenSSLPKCS8DecryptorProviderBuilder()
                            .build(keyPassword.toCharArray());
                    pk = converter.getPrivateKey(((PKCS8EncryptedPrivateKeyInfo) object)
                            .decryptPrivateKeyInfo(pkcs8InputDecryptorProvider));
                } else {
                    logger.debug("Unable to handle PEM object of type " + object.getClass() + " as a encrypted key");
                }
            }

            if (pk == null) {
                logger.debug("No key found");
            }

            return pk;
        } finally {
            if (pemParser != null) {
                try {
                    pemParser.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    private static PEMParser getParser(File keyFile) throws FileNotFoundException {
        return new PEMParser(new FileReader(keyFile));
    }

    private static PEMParser getParser(InputStream keyInputStream) {
        return new PEMParser(new InputStreamReader(keyInputStream, CharsetUtil.US_ASCII));
    }

    private BouncyCastlePemReader() {
    }

}
