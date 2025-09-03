/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.ssl.util;

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Date;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;

import static io.netty.handler.ssl.util.SelfSignedCertificate.*;

/**
 * Generates a self-signed certificate using {@code sun.security.x509} package provided by OpenJDK.
 */
final class OpenJdkSelfSignedCertGenerator {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OpenJdkSelfSignedCertGenerator.class);
    private static final Method CERT_INFO_SET_METHOD;
    private static final Constructor<?> ISSUER_NAME_CONSTRUCTOR;
    private static final Constructor<X509CertImpl> CERT_IMPL_CONSTRUCTOR;
    private static final Method CERT_IMPL_GET_METHOD;
    private static final Method CERT_IMPL_SIGN_METHOD;

    // Use reflection as JDK20+ did change things quite a bit.
    static {
        Method certInfoSetMethod = null;
        Constructor<?> issuerNameConstructor = null;
        Constructor<X509CertImpl> certImplConstructor = null;
        Method certImplGetMethod = null;
        Method certImplSignMethod = null;
        try {
            Object maybeCertInfoSetMethod = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        return X509CertInfo.class.getMethod("set", String.class, Object.class);
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            });
            if (maybeCertInfoSetMethod instanceof Method) {
                certInfoSetMethod = (Method) maybeCertInfoSetMethod;
            } else {
                throw (Throwable) maybeCertInfoSetMethod;
            }

            Object maybeIssuerNameConstructor = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        Class<?> issuerName = Class.forName("sun.security.x509.CertificateIssuerName", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class));
                        return issuerName.getConstructor(X500Name.class);
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            });
            if (maybeIssuerNameConstructor instanceof Constructor) {
                issuerNameConstructor = (Constructor<?>) maybeIssuerNameConstructor;
            } else {
                throw (Throwable) maybeIssuerNameConstructor;
            }

            Object maybeCertImplConstructor = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        return X509CertImpl.class.getConstructor(X509CertInfo.class);
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            });
            if (maybeCertImplConstructor instanceof Constructor) {
                @SuppressWarnings("unchecked")
                Constructor<X509CertImpl> constructor = (Constructor<X509CertImpl>) maybeCertImplConstructor;
                certImplConstructor = constructor;
            } else {
                throw (Throwable) maybeCertImplConstructor;
            }

            Object maybeCertImplGetMethod = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        return X509CertImpl.class.getMethod("get", String.class);
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            });
            if (maybeCertImplGetMethod instanceof Method) {
                certImplGetMethod = (Method) maybeCertImplGetMethod;
            } else {
                throw (Throwable) maybeCertImplGetMethod;
            }

            Object maybeCertImplSignMethod = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        return X509CertImpl.class.getMethod("sign", PrivateKey.class, String.class);
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            });
            if (maybeCertImplSignMethod instanceof Method) {
                certImplSignMethod = (Method) maybeCertImplSignMethod;
            } else {
                throw (Throwable) maybeCertImplSignMethod;
            }
        } catch (Throwable cause) {
            logger.debug(OpenJdkSelfSignedCertGenerator.class.getSimpleName() + " not supported", cause);
        }
        CERT_INFO_SET_METHOD = certInfoSetMethod;
        ISSUER_NAME_CONSTRUCTOR = issuerNameConstructor;
        CERT_IMPL_CONSTRUCTOR = certImplConstructor;
        CERT_IMPL_GET_METHOD = certImplGetMethod;
        CERT_IMPL_SIGN_METHOD = certImplSignMethod;
    }

    @SuppressJava6Requirement(reason = "Usage guarded by dependency check")
    static String[] generate(String fqdn, KeyPair keypair, SecureRandom random, Date notBefore, Date notAfter,
                             String algorithm) throws Exception {
        if (CERT_INFO_SET_METHOD == null || ISSUER_NAME_CONSTRUCTOR == null ||
                CERT_IMPL_CONSTRUCTOR == null || CERT_IMPL_GET_METHOD == null || CERT_IMPL_SIGN_METHOD == null) {
            throw new UnsupportedOperationException(
                    OpenJdkSelfSignedCertGenerator.class.getSimpleName() + " not supported on the used JDK version");
        }
        PrivateKey key = keypair.getPrivate();

        // Prepare the information required for generating an X.509 certificate.
        X509CertInfo info = new X509CertInfo();
        X500Name owner = new X500Name("CN=" + fqdn);

        CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.SERIAL_NUMBER,
                new CertificateSerialNumber(new BigInteger(64, random)));
        try {
            CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.SUBJECT, new CertificateSubjectName(owner));
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof CertificateException) {
                CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.SUBJECT, owner);
            } else {
                throw ex;
            }
        }
        try {
            CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.ISSUER, ISSUER_NAME_CONSTRUCTOR.newInstance(owner));
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof CertificateException) {
                CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.ISSUER, owner);
            } else {
                throw ex;
            }
        }
        CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.VALIDITY, new CertificateValidity(notBefore, notAfter));
        CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.KEY, new CertificateX509Key(keypair.getPublic()));
        CERT_INFO_SET_METHOD.invoke(info, X509CertInfo.ALGORITHM_ID,
                // sha256WithRSAEncryption
                new CertificateAlgorithmId(AlgorithmId.get("1.2.840.113549.1.1.11")));

        // Sign the cert to identify the algorithm that's used.
        X509CertImpl cert = CERT_IMPL_CONSTRUCTOR.newInstance(info);
        CERT_IMPL_SIGN_METHOD.invoke(cert, key, algorithm.equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA");

        // Update the algorithm and sign again.
        CERT_INFO_SET_METHOD.invoke(info, CertificateAlgorithmId.NAME + ".algorithm",
                CERT_IMPL_GET_METHOD.invoke(cert, "x509.algorithm"));
        cert = CERT_IMPL_CONSTRUCTOR.newInstance(info);
        CERT_IMPL_SIGN_METHOD.invoke(cert, key,
                algorithm.equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA");
        cert.verify(keypair.getPublic());

        return newSelfSignedCertificate(fqdn, key, cert);
    }

    private OpenJdkSelfSignedCertGenerator() { }
}
