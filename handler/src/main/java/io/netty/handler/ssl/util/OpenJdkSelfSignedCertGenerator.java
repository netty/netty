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
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.security.AccessController;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static io.netty.handler.ssl.util.SelfSignedCertificate.newSelfSignedCertificate;
import static java.lang.invoke.MethodType.methodType;

/**
 * Generates a self-signed certificate using {@code sun.security.x509} package provided by OpenJDK.
 */
final class OpenJdkSelfSignedCertGenerator {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(OpenJdkSelfSignedCertGenerator.class);
    private static final MethodHandle CERT_INFO_SET_HANDLE;
    private static final MethodHandle ISSUER_NAME_CONSTRUCTOR;
    private static final MethodHandle CERT_IMPL_CONSTRUCTOR;
    private static final MethodHandle X509_CERT_INFO_CONSTRUCTOR;
    private static final MethodHandle CERTIFICATE_VERSION_CONSTRUCTOR;
    private static final MethodHandle CERTIFICATE_SUBJECT_NAME_CONSTRUCTOR;
    private static final MethodHandle X500_NAME_CONSTRUCTOR;
    private static final MethodHandle CERTIFICATE_SERIAL_NUMBER_CONSTRUCTOR;
    private static final MethodHandle CERTIFICATE_VALIDITY_CONSTRUCTOR;
    private static final MethodHandle CERTIFICATE_X509_KEY_CONSTRUCTOR;
    private static final MethodHandle CERTIFICATE_ALORITHM_ID_CONSTRUCTOR;
    private static final MethodHandle CERT_IMPL_GET_HANDLE;
    private static final MethodHandle CERT_IMPL_SIGN_HANDLE;
    private static final MethodHandle ALGORITHM_ID_GET_HANDLE;

    private static final boolean SUPPORTED;

    // Use reflection as JDK20+ did change things quite a bit.
    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();

        final Class<?> x509CertInfoClass;
        final Class<?> x500NameClass;
        final Class<?> certificateIssuerNameClass;
        final Class<?> x509CertImplClass;
        final Class<?> certificateVersionClass;
        final Class<?> certificateSubjectNameClass;
        final Class<?> certificateSerialNumberClass;
        final Class<?> certificateValidityClass;
        final Class<?> certificateX509KeyClass;
        final Class<?> algorithmIdClass;
        final Class<?> certificateAlgorithmIdClass;

        boolean supported;
        MethodHandle certInfoSetHandle = null;
        MethodHandle x509CertInfoConstructor = null;
        MethodHandle issuerNameConstructor = null;
        MethodHandle certImplConstructor = null;
        MethodHandle x500NameConstructor = null;
        MethodHandle certificateVersionConstructor = null;
        MethodHandle certificateSubjectNameConstructor = null;
        MethodHandle certificateSerialNumberConstructor = null;
        MethodHandle certificateValidityConstructor = null;
        MethodHandle certificateX509KeyConstructor = null;
        MethodHandle certificateAlgorithmIdConstructor = null;
        MethodHandle certImplGetHandle = null;
        MethodHandle certImplSignHandle = null;
        MethodHandle algorithmIdGetHandle = null;

        try {
            Object maybeClasses = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        List<Class<?>> classes = new ArrayList<>();
                        classes.add(Class.forName("sun.security.x509.X509CertInfo", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.X500Name", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.CertificateIssuerName", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.X509CertImpl", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.CertificateVersion", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.CertificateSubjectName", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.CertificateSerialNumber", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.CertificateValidity", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.CertificateX509Key", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.AlgorithmId", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));
                        classes.add(Class.forName("sun.security.x509.CertificateAlgorithmId", false,
                                PlatformDependent.getClassLoader(OpenJdkSelfSignedCertGenerator.class)));

                        return classes;
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            });
            if (maybeClasses instanceof List) {
                @SuppressWarnings("unchecked") List<Class<?>> classes = (List<Class<?>>) maybeClasses;
                x509CertInfoClass = classes.get(0);
                x500NameClass = classes.get(1);
                certificateIssuerNameClass = classes.get(2);
                x509CertImplClass = classes.get(3);
                certificateVersionClass = classes.get(4);
                certificateSubjectNameClass = classes.get(5);
                certificateSerialNumberClass = classes.get(6);
                certificateValidityClass = classes.get(7);
                certificateX509KeyClass = classes.get(8);
                algorithmIdClass = classes.get(9);
                certificateAlgorithmIdClass = classes.get(10);
            } else {
                throw (Throwable) maybeClasses;
            }

            Object maybeConstructors = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        List<MethodHandle> constructors = new ArrayList<>();
                        constructors.add(
                                lookup.unreflectConstructor(x509CertInfoClass.getConstructor())
                                        .asType(methodType(x509CertInfoClass))
                        );
                        constructors.add(
                                lookup.unreflectConstructor(certificateIssuerNameClass.getConstructor(x500NameClass))
                                        .asType(methodType(certificateIssuerNameClass, x500NameClass))
                        );
                        constructors.add(
                                lookup.unreflectConstructor(x509CertImplClass.getConstructor(x509CertInfoClass))
                                        .asType(methodType(x509CertImplClass, x509CertInfoClass))
                        );
                        constructors.add(
                                lookup.unreflectConstructor(x500NameClass.getConstructor(String.class))
                                        .asType(methodType(x500NameClass, String.class))
                        );
                        constructors.add(
                                lookup.unreflectConstructor(certificateVersionClass.getConstructor(int.class))
                                        .asType(methodType(certificateVersionClass, int.class))
                        );
                        constructors.add(
                                lookup.unreflectConstructor(certificateSubjectNameClass.getConstructor(x500NameClass))
                                        .asType(methodType(certificateSubjectNameClass, x500NameClass))
                        );
                        constructors.add(
                                lookup.unreflectConstructor(
                                        certificateSerialNumberClass.getConstructor(BigInteger.class))
                                        .asType(methodType(certificateSerialNumberClass, BigInteger.class))
                        );
                        constructors.add(
                                lookup.unreflectConstructor(
                                        certificateValidityClass.getConstructor(Date.class, Date.class))
                                        .asType(methodType(certificateValidityClass, Date.class, Date.class))
                        );
                        constructors.add(
                                lookup.unreflectConstructor(certificateX509KeyClass.getConstructor(PublicKey.class))
                                        .asType(methodType(certificateX509KeyClass, PublicKey.class))
                        );

                        constructors.add(
                                lookup.unreflectConstructor(
                                        certificateAlgorithmIdClass.getConstructor(algorithmIdClass))
                                        .asType(methodType(certificateAlgorithmIdClass, algorithmIdClass))
                        );
                        return constructors;
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            });
            if (maybeConstructors instanceof List) {
                @SuppressWarnings("unchecked") List<MethodHandle> constructorList =
                        (List<MethodHandle>) maybeConstructors;
                x509CertInfoConstructor = constructorList.get(0);
                issuerNameConstructor = constructorList.get(1);
                certImplConstructor = constructorList.get(2);
                x500NameConstructor = constructorList.get(3);
                certificateVersionConstructor = constructorList.get(4);
                certificateSubjectNameConstructor = constructorList.get(5);
                certificateSerialNumberConstructor = constructorList.get(6);
                certificateValidityConstructor = constructorList.get(7);
                certificateX509KeyConstructor = constructorList.get(8);
                certificateAlgorithmIdConstructor = constructorList.get(9);
            } else {
                throw (Throwable) maybeConstructors;
            }

            Object maybeMethodHandles = AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    try {
                        List<MethodHandle> methods = new ArrayList<>();
                        methods.add(
                                lookup.findVirtual(x509CertInfoClass, "set",
                                        methodType(void.class, String.class, Object.class))
                        );
                        methods.add(
                                lookup.findVirtual(x509CertImplClass, "get",
                                        methodType(Object.class, String.class))
                        );

                        methods.add(
                                lookup.findVirtual(x509CertImplClass, "sign",
                                        methodType(void.class, PrivateKey.class, String.class))
                        );
                        methods.add(
                                lookup.findStatic(algorithmIdClass, "get",
                                        methodType(algorithmIdClass, String.class))
                        );
                        return methods;
                    } catch (Throwable cause) {
                        return cause;
                    }
                }
            });
            if (maybeMethodHandles instanceof List) {
                @SuppressWarnings("unchecked") List<MethodHandle> methodHandles =
                        (List<MethodHandle>) maybeMethodHandles;
                certInfoSetHandle = methodHandles.get(0);
                certImplGetHandle = methodHandles.get(1);
                certImplSignHandle = methodHandles.get(2);
                algorithmIdGetHandle = methodHandles.get(3);
            } else {
                throw (Throwable) maybeMethodHandles;
            }
            supported = true;
        } catch (Throwable cause) {
            supported = false;
            logger.debug(OpenJdkSelfSignedCertGenerator.class.getSimpleName() + " not supported", cause);
        }
        CERT_INFO_SET_HANDLE = certInfoSetHandle;
        X509_CERT_INFO_CONSTRUCTOR = x509CertInfoConstructor;
        ISSUER_NAME_CONSTRUCTOR = issuerNameConstructor;
        CERTIFICATE_VERSION_CONSTRUCTOR = certificateVersionConstructor;
        CERTIFICATE_SUBJECT_NAME_CONSTRUCTOR = certificateSubjectNameConstructor;
        CERT_IMPL_CONSTRUCTOR = certImplConstructor;
        X500_NAME_CONSTRUCTOR = x500NameConstructor;
        CERTIFICATE_SERIAL_NUMBER_CONSTRUCTOR = certificateSerialNumberConstructor;
        CERTIFICATE_VALIDITY_CONSTRUCTOR = certificateValidityConstructor;
        CERTIFICATE_X509_KEY_CONSTRUCTOR = certificateX509KeyConstructor;
        CERT_IMPL_GET_HANDLE = certImplGetHandle;
        CERT_IMPL_SIGN_HANDLE = certImplSignHandle;
        ALGORITHM_ID_GET_HANDLE = algorithmIdGetHandle;
        CERTIFICATE_ALORITHM_ID_CONSTRUCTOR = certificateAlgorithmIdConstructor;
        SUPPORTED = supported;
    }

    static String[] generate(String fqdn, KeyPair keypair, SecureRandom random, Date notBefore, Date notAfter,
                             String algorithm) throws Exception {
        if (!SUPPORTED) {
            throw new UnsupportedOperationException(
                    OpenJdkSelfSignedCertGenerator.class.getSimpleName() + " not supported on the used JDK version");
        }
        try {
            PrivateKey key = keypair.getPrivate();

            // Prepare the information required for generating an X.509 certificate.
            Object info = X509_CERT_INFO_CONSTRUCTOR.invoke();
            Object owner = X500_NAME_CONSTRUCTOR.invoke("CN=" + fqdn);

            CERT_INFO_SET_HANDLE.invoke(info, "version", CERTIFICATE_VERSION_CONSTRUCTOR.invoke(2));
            CERT_INFO_SET_HANDLE.invoke(info, "serialNumber",
                    CERTIFICATE_SERIAL_NUMBER_CONSTRUCTOR.invoke(new BigInteger(64, random)));
            try {
                CERT_INFO_SET_HANDLE.invoke(info, "subject", CERTIFICATE_SUBJECT_NAME_CONSTRUCTOR.invoke(owner));
            } catch (CertificateException ex) {
                CERT_INFO_SET_HANDLE.invoke(info, "subject", owner);
            }
            try {
                CERT_INFO_SET_HANDLE.invoke(info, "issuer", ISSUER_NAME_CONSTRUCTOR.invoke(owner));
            } catch (CertificateException ex) {
                CERT_INFO_SET_HANDLE.invoke(info, "issuer", owner);
            }
            CERT_INFO_SET_HANDLE.invoke(info, "validity",
                    CERTIFICATE_VALIDITY_CONSTRUCTOR.invoke(notBefore, notAfter));
            CERT_INFO_SET_HANDLE.invoke(info, "key", CERTIFICATE_X509_KEY_CONSTRUCTOR.invoke(keypair.getPublic()));
            CERT_INFO_SET_HANDLE.invoke(info, "algorithmID",
                    // sha256WithRSAEncryption
                    CERTIFICATE_ALORITHM_ID_CONSTRUCTOR.invoke(
                            ALGORITHM_ID_GET_HANDLE.invoke("1.2.840.113549.1.1.11")));

            // Sign the cert to identify the algorithm that's used.
            Object cert = CERT_IMPL_CONSTRUCTOR.invoke(info);
            CERT_IMPL_SIGN_HANDLE.invoke(cert, key,
                    algorithm.equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA");

            // Update the algorithm and sign again.
            CERT_INFO_SET_HANDLE.invoke(info, "algorithmID.algorithm",
                    CERT_IMPL_GET_HANDLE.invoke(cert, "x509.algorithm"));
            cert = CERT_IMPL_CONSTRUCTOR.invoke(info);
            CERT_IMPL_SIGN_HANDLE.invoke(cert, key,
                    algorithm.equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA");

            X509Certificate x509Cert = (X509Certificate) cert;
            x509Cert.verify(keypair.getPublic());

            return newSelfSignedCertificate(fqdn, key, x509Cert);
        } catch (Throwable cause) {
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        }
    }

    private OpenJdkSelfSignedCertGenerator() { }
}
