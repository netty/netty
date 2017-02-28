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

import io.netty.internal.tcnative.CertificateVerifier;
import io.netty.util.collection.IntCollections;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.security.cert.CertificateException;

/**
 * A special {@link CertificateException} which allows to specify which error code is included in the
 * SSL Record. This only work when {@link SslProvider#OPENSSL} is used.
 */
public final class OpenSslCertificateException extends CertificateException {
    public static final int X509_V_OK;
    public static final int X509_V_ERR_UNSPECIFIED;
    public static final int X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT;
    public static final int X509_V_ERR_UNABLE_TO_GET_CRL;
    public static final int X509_V_ERR_UNABLE_TO_DECRYPT_CERT_SIGNATURE;
    public static final int X509_V_ERR_UNABLE_TO_DECRYPT_CRL_SIGNATURE;
    public static final int X509_V_ERR_UNABLE_TO_DECODE_ISSUER_PUBLIC_KEY;
    public static final int X509_V_ERR_CERT_SIGNATURE_FAILURE;
    public static final int X509_V_ERR_CRL_SIGNATURE_FAILURE;
    public static final int X509_V_ERR_CERT_NOT_YET_VALID;
    public static final int X509_V_ERR_CERT_HAS_EXPIRED;
    public static final int X509_V_ERR_CRL_NOT_YET_VALID;
    public static final int X509_V_ERR_CRL_HAS_EXPIRED;
    public static final int X509_V_ERR_ERROR_IN_CERT_NOT_BEFORE_FIELD;
    public static final int X509_V_ERR_ERROR_IN_CERT_NOT_AFTER_FIELD;
    public static final int X509_V_ERR_ERROR_IN_CRL_LAST_UPDATE_FIELD;
    public static final int X509_V_ERR_ERROR_IN_CRL_NEXT_UPDATE_FIELD;
    public static final int X509_V_ERR_OUT_OF_MEM;
    public static final int X509_V_ERR_DEPTH_ZERO_SELF_SIGNED_CERT;
    public static final int X509_V_ERR_SELF_SIGNED_CERT_IN_CHAIN;
    public static final int X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY;
    public static final int X509_V_ERR_UNABLE_TO_VERIFY_LEAF_SIGNATURE;
    public static final int X509_V_ERR_CERT_CHAIN_TOO_LONG;
    public static final int X509_V_ERR_CERT_REVOKED;
    public static final int X509_V_ERR_INVALID_CA;
    public static final int X509_V_ERR_PATH_LENGTH_EXCEEDED;
    public static final int X509_V_ERR_INVALID_PURPOSE;
    public static final int X509_V_ERR_CERT_UNTRUSTED;
    public static final int X509_V_ERR_CERT_REJECTED;
    public static final int X509_V_ERR_SUBJECT_ISSUER_MISMATCH;
    public static final int X509_V_ERR_AKID_SKID_MISMATCH;
    public static final int X509_V_ERR_AKID_ISSUER_SERIAL_MISMATCH;
    public static final int X509_V_ERR_KEYUSAGE_NO_CERTSIGN;
    public static final int X509_V_ERR_UNABLE_TO_GET_CRL_ISSUER;
    public static final int X509_V_ERR_UNHANDLED_CRITICAL_EXTENSION;
    public static final int X509_V_ERR_KEYUSAGE_NO_CRL_SIGN;
    public static final int X509_V_ERR_UNHANDLED_CRITICAL_CRL_EXTENSION;
    public static final int X509_V_ERR_INVALID_NON_CA;
    public static final int X509_V_ERR_PROXY_PATH_LENGTH_EXCEEDED;
    public static final int X509_V_ERR_KEYUSAGE_NO_DIGITAL_SIGNATURE;
    public static final int X509_V_ERR_PROXY_CERTIFICATES_NOT_ALLOWED;
    public static final int X509_V_ERR_INVALID_EXTENSION;
    public static final int X509_V_ERR_INVALID_POLICY_EXTENSION;
    public static final int X509_V_ERR_NO_EXPLICIT_POLICY;
    public static final int X509_V_ERR_DIFFERENT_CRL_SCOPE;
    public static final int X509_V_ERR_UNSUPPORTED_EXTENSION_FEATURE;
    public static final int X509_V_ERR_UNNESTED_RESOURCE;
    public static final int X509_V_ERR_PERMITTED_VIOLATION;
    public static final int X509_V_ERR_EXCLUDED_VIOLATION;
    public static final int X509_V_ERR_SUBTREE_MINMAX;
    public static final int X509_V_ERR_APPLICATION_VERIFICATION;
    public static final int X509_V_ERR_UNSUPPORTED_CONSTRAINT_TYPE;
    public static final int X509_V_ERR_UNSUPPORTED_CONSTRAINT_SYNTAX;
    public static final int X509_V_ERR_UNSUPPORTED_NAME_SYNTAX;
    public static final int X509_V_ERR_CRL_PATH_VALIDATION_ERROR;
    public static final int X509_V_ERR_PATH_LOOP;
    public static final int X509_V_ERR_SUITE_B_INVALID_VERSION;
    public static final int X509_V_ERR_SUITE_B_INVALID_ALGORITHM;
    public static final int X509_V_ERR_SUITE_B_INVALID_CURVE;
    public static final int X509_V_ERR_SUITE_B_INVALID_SIGNATURE_ALGORITHM;
    public static final int X509_V_ERR_SUITE_B_LOS_NOT_ALLOWED;
    public static final int X509_V_ERR_SUITE_B_CANNOT_SIGN_P_384_WITH_P_256;
    public static final int X509_V_ERR_HOSTNAME_MISMATCH;
    public static final int X509_V_ERR_EMAIL_MISMATCH;
    public static final int X509_V_ERR_IP_ADDRESS_MISMATCH;
    public static final int X509_V_ERR_DANE_NO_MATCH;
    private static final IntObjectMap<Boolean> ERROR_MAP;

    static {
        OpenSsl.ensureAvailability();

        IntObjectHashMap<Boolean> errors = new IntObjectHashMap<Boolean>();

        X509_V_OK = CertificateVerifier.X509_V_OK;
        errors.put(X509_V_OK, Boolean.TRUE);

        X509_V_ERR_UNSPECIFIED = CertificateVerifier.X509_V_ERR_UNSPECIFIED;
        errors.put(X509_V_ERR_UNSPECIFIED, Boolean.TRUE);

        X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT = CertificateVerifier.X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT;
        errors.put(X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT, Boolean.TRUE);

        X509_V_ERR_UNABLE_TO_GET_CRL = CertificateVerifier.X509_V_ERR_UNABLE_TO_GET_CRL;
        errors.put(X509_V_ERR_UNABLE_TO_GET_CRL, Boolean.TRUE);

        X509_V_ERR_UNABLE_TO_DECRYPT_CERT_SIGNATURE =
                CertificateVerifier.X509_V_ERR_UNABLE_TO_DECRYPT_CERT_SIGNATURE;
        errors.put(X509_V_ERR_UNABLE_TO_DECRYPT_CERT_SIGNATURE, Boolean.TRUE);

        X509_V_ERR_UNABLE_TO_DECRYPT_CRL_SIGNATURE = CertificateVerifier.X509_V_ERR_UNABLE_TO_DECRYPT_CRL_SIGNATURE;
        errors.put(X509_V_ERR_UNABLE_TO_DECRYPT_CRL_SIGNATURE, Boolean.TRUE);

        X509_V_ERR_UNABLE_TO_DECODE_ISSUER_PUBLIC_KEY =
                CertificateVerifier.X509_V_ERR_UNABLE_TO_DECODE_ISSUER_PUBLIC_KEY;
        errors.put(X509_V_ERR_UNABLE_TO_DECODE_ISSUER_PUBLIC_KEY, Boolean.TRUE);

        X509_V_ERR_CERT_SIGNATURE_FAILURE = CertificateVerifier.X509_V_ERR_CERT_SIGNATURE_FAILURE;
        errors.put(X509_V_ERR_CERT_SIGNATURE_FAILURE, Boolean.TRUE);

        X509_V_ERR_CRL_SIGNATURE_FAILURE = CertificateVerifier.X509_V_ERR_CRL_SIGNATURE_FAILURE;
        errors.put(X509_V_ERR_CRL_SIGNATURE_FAILURE, Boolean.TRUE);

        X509_V_ERR_CERT_NOT_YET_VALID = CertificateVerifier.X509_V_ERR_CERT_NOT_YET_VALID;
        errors.put(X509_V_ERR_CERT_NOT_YET_VALID, Boolean.TRUE);

        X509_V_ERR_CERT_HAS_EXPIRED = CertificateVerifier.X509_V_ERR_CERT_HAS_EXPIRED;
        errors.put(X509_V_ERR_CERT_HAS_EXPIRED, Boolean.TRUE);

        X509_V_ERR_CRL_NOT_YET_VALID = CertificateVerifier.X509_V_ERR_CRL_NOT_YET_VALID;
        errors.put(X509_V_ERR_CRL_NOT_YET_VALID, Boolean.TRUE);

        X509_V_ERR_CRL_HAS_EXPIRED = CertificateVerifier.X509_V_ERR_CRL_HAS_EXPIRED;
        errors.put(X509_V_ERR_CRL_HAS_EXPIRED, Boolean.TRUE);

        X509_V_ERR_ERROR_IN_CERT_NOT_BEFORE_FIELD = CertificateVerifier.X509_V_ERR_ERROR_IN_CERT_NOT_BEFORE_FIELD;
        errors.put(X509_V_ERR_ERROR_IN_CERT_NOT_BEFORE_FIELD, Boolean.TRUE);

        X509_V_ERR_ERROR_IN_CERT_NOT_AFTER_FIELD = CertificateVerifier.X509_V_ERR_ERROR_IN_CERT_NOT_AFTER_FIELD;
        errors.put(X509_V_ERR_ERROR_IN_CERT_NOT_AFTER_FIELD, Boolean.TRUE);

        X509_V_ERR_ERROR_IN_CRL_LAST_UPDATE_FIELD = CertificateVerifier.X509_V_ERR_ERROR_IN_CRL_LAST_UPDATE_FIELD;
        errors.put(X509_V_ERR_ERROR_IN_CRL_LAST_UPDATE_FIELD, Boolean.TRUE);

        X509_V_ERR_ERROR_IN_CRL_NEXT_UPDATE_FIELD = CertificateVerifier.X509_V_ERR_ERROR_IN_CRL_NEXT_UPDATE_FIELD;
        errors.put(X509_V_ERR_ERROR_IN_CRL_NEXT_UPDATE_FIELD, Boolean.TRUE);

        X509_V_ERR_OUT_OF_MEM = CertificateVerifier.X509_V_ERR_OUT_OF_MEM;
        errors.put(X509_V_ERR_OUT_OF_MEM, Boolean.TRUE);

        X509_V_ERR_DEPTH_ZERO_SELF_SIGNED_CERT = CertificateVerifier.X509_V_ERR_DEPTH_ZERO_SELF_SIGNED_CERT;
        errors.put(X509_V_ERR_DEPTH_ZERO_SELF_SIGNED_CERT, Boolean.TRUE);

        X509_V_ERR_SELF_SIGNED_CERT_IN_CHAIN = CertificateVerifier.X509_V_ERR_SELF_SIGNED_CERT_IN_CHAIN;
        errors.put(X509_V_ERR_SELF_SIGNED_CERT_IN_CHAIN, Boolean.TRUE);

        X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY =
                CertificateVerifier.X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY;
        errors.put(X509_V_ERR_UNABLE_TO_GET_ISSUER_CERT_LOCALLY, Boolean.TRUE);

        X509_V_ERR_UNABLE_TO_VERIFY_LEAF_SIGNATURE = CertificateVerifier.X509_V_ERR_UNABLE_TO_VERIFY_LEAF_SIGNATURE;
        errors.put(X509_V_ERR_UNABLE_TO_VERIFY_LEAF_SIGNATURE, Boolean.TRUE);

        X509_V_ERR_CERT_CHAIN_TOO_LONG = CertificateVerifier.X509_V_ERR_CERT_CHAIN_TOO_LONG;
        errors.put(X509_V_ERR_CERT_CHAIN_TOO_LONG, Boolean.TRUE);

        X509_V_ERR_CERT_REVOKED = CertificateVerifier.X509_V_ERR_CERT_REVOKED;
        errors.put(X509_V_ERR_CERT_REVOKED, Boolean.TRUE);

        X509_V_ERR_INVALID_CA = CertificateVerifier.X509_V_ERR_INVALID_CA;
        errors.put(X509_V_ERR_INVALID_CA, Boolean.TRUE);

        X509_V_ERR_PATH_LENGTH_EXCEEDED = CertificateVerifier.X509_V_ERR_PATH_LENGTH_EXCEEDED;
        errors.put(X509_V_ERR_PATH_LENGTH_EXCEEDED, Boolean.TRUE);

        X509_V_ERR_INVALID_PURPOSE = CertificateVerifier.X509_V_ERR_INVALID_PURPOSE;
        errors.put(X509_V_ERR_INVALID_PURPOSE, Boolean.TRUE);

        X509_V_ERR_CERT_UNTRUSTED = CertificateVerifier.X509_V_ERR_CERT_UNTRUSTED;
        errors.put(X509_V_ERR_CERT_UNTRUSTED, Boolean.TRUE);

        X509_V_ERR_CERT_REJECTED = CertificateVerifier.X509_V_ERR_CERT_REJECTED;
        errors.put(X509_V_ERR_CERT_REJECTED, Boolean.TRUE);

        X509_V_ERR_SUBJECT_ISSUER_MISMATCH = CertificateVerifier.X509_V_ERR_SUBJECT_ISSUER_MISMATCH;
        errors.put(X509_V_ERR_SUBJECT_ISSUER_MISMATCH, Boolean.TRUE);

        X509_V_ERR_AKID_SKID_MISMATCH = CertificateVerifier.X509_V_ERR_AKID_SKID_MISMATCH;
        errors.put(X509_V_ERR_AKID_SKID_MISMATCH, Boolean.TRUE);

        X509_V_ERR_AKID_ISSUER_SERIAL_MISMATCH = CertificateVerifier.X509_V_ERR_AKID_ISSUER_SERIAL_MISMATCH;
        errors.put(X509_V_ERR_AKID_ISSUER_SERIAL_MISMATCH, Boolean.TRUE);

        X509_V_ERR_KEYUSAGE_NO_CERTSIGN = CertificateVerifier.X509_V_ERR_KEYUSAGE_NO_CERTSIGN;
        errors.put(X509_V_ERR_KEYUSAGE_NO_CERTSIGN, Boolean.TRUE);

        X509_V_ERR_UNABLE_TO_GET_CRL_ISSUER = CertificateVerifier.X509_V_ERR_UNABLE_TO_GET_CRL_ISSUER;
        errors.put(X509_V_ERR_UNABLE_TO_GET_CRL_ISSUER, Boolean.TRUE);

        X509_V_ERR_UNHANDLED_CRITICAL_EXTENSION = CertificateVerifier.X509_V_ERR_UNHANDLED_CRITICAL_EXTENSION;
        errors.put(X509_V_ERR_UNHANDLED_CRITICAL_EXTENSION, Boolean.TRUE);

        X509_V_ERR_KEYUSAGE_NO_CRL_SIGN = CertificateVerifier.X509_V_ERR_KEYUSAGE_NO_CRL_SIGN;
        errors.put(X509_V_ERR_KEYUSAGE_NO_CRL_SIGN, Boolean.TRUE);

        X509_V_ERR_UNHANDLED_CRITICAL_CRL_EXTENSION =
                CertificateVerifier.X509_V_ERR_UNHANDLED_CRITICAL_CRL_EXTENSION;
        errors.put(X509_V_ERR_UNHANDLED_CRITICAL_CRL_EXTENSION, Boolean.TRUE);

        X509_V_ERR_INVALID_NON_CA = CertificateVerifier.X509_V_ERR_INVALID_NON_CA;
        errors.put(X509_V_ERR_INVALID_NON_CA, Boolean.TRUE);

        X509_V_ERR_PROXY_PATH_LENGTH_EXCEEDED = CertificateVerifier.X509_V_ERR_PROXY_PATH_LENGTH_EXCEEDED;
        errors.put(X509_V_ERR_PROXY_PATH_LENGTH_EXCEEDED, Boolean.TRUE);

        X509_V_ERR_KEYUSAGE_NO_DIGITAL_SIGNATURE = CertificateVerifier.X509_V_ERR_KEYUSAGE_NO_DIGITAL_SIGNATURE;
        errors.put(X509_V_ERR_KEYUSAGE_NO_DIGITAL_SIGNATURE, Boolean.TRUE);

        X509_V_ERR_PROXY_CERTIFICATES_NOT_ALLOWED = CertificateVerifier.X509_V_ERR_PROXY_CERTIFICATES_NOT_ALLOWED;
        errors.put(X509_V_ERR_PROXY_CERTIFICATES_NOT_ALLOWED, Boolean.TRUE);

        X509_V_ERR_INVALID_EXTENSION = CertificateVerifier.X509_V_ERR_INVALID_EXTENSION;
        errors.put(X509_V_ERR_INVALID_EXTENSION, Boolean.TRUE);

        X509_V_ERR_INVALID_POLICY_EXTENSION = CertificateVerifier.X509_V_ERR_INVALID_POLICY_EXTENSION;
        errors.put(X509_V_ERR_INVALID_POLICY_EXTENSION, Boolean.TRUE);

        X509_V_ERR_NO_EXPLICIT_POLICY = CertificateVerifier.X509_V_ERR_NO_EXPLICIT_POLICY;
        errors.put(X509_V_ERR_NO_EXPLICIT_POLICY, Boolean.TRUE);

        X509_V_ERR_DIFFERENT_CRL_SCOPE = CertificateVerifier.X509_V_ERR_DIFFERENT_CRL_SCOPE;
        errors.put(X509_V_ERR_DIFFERENT_CRL_SCOPE, Boolean.TRUE);

        X509_V_ERR_UNSUPPORTED_EXTENSION_FEATURE = CertificateVerifier.X509_V_ERR_UNSUPPORTED_EXTENSION_FEATURE;
        errors.put(X509_V_ERR_UNSUPPORTED_EXTENSION_FEATURE, Boolean.TRUE);

        X509_V_ERR_UNNESTED_RESOURCE = CertificateVerifier.X509_V_ERR_UNNESTED_RESOURCE;
        errors.put(X509_V_ERR_UNNESTED_RESOURCE, Boolean.TRUE);

        X509_V_ERR_PERMITTED_VIOLATION = CertificateVerifier.X509_V_ERR_PERMITTED_VIOLATION;
        errors.put(X509_V_ERR_PERMITTED_VIOLATION, Boolean.TRUE);

        X509_V_ERR_EXCLUDED_VIOLATION = CertificateVerifier.X509_V_ERR_EXCLUDED_VIOLATION;
        errors.put(X509_V_ERR_EXCLUDED_VIOLATION, Boolean.TRUE);

        X509_V_ERR_SUBTREE_MINMAX = CertificateVerifier.X509_V_ERR_SUBTREE_MINMAX;
        errors.put(X509_V_ERR_SUBTREE_MINMAX, Boolean.TRUE);

        X509_V_ERR_APPLICATION_VERIFICATION = CertificateVerifier.X509_V_ERR_APPLICATION_VERIFICATION;
        errors.put(X509_V_ERR_APPLICATION_VERIFICATION, Boolean.TRUE);

        X509_V_ERR_UNSUPPORTED_CONSTRAINT_TYPE = CertificateVerifier.X509_V_ERR_UNSUPPORTED_CONSTRAINT_TYPE;
        errors.put(X509_V_ERR_UNSUPPORTED_CONSTRAINT_TYPE, Boolean.TRUE);

        X509_V_ERR_UNSUPPORTED_CONSTRAINT_SYNTAX = CertificateVerifier.X509_V_ERR_UNSUPPORTED_CONSTRAINT_SYNTAX;
        errors.put(X509_V_ERR_UNSUPPORTED_CONSTRAINT_SYNTAX, Boolean.TRUE);

        X509_V_ERR_UNSUPPORTED_NAME_SYNTAX = CertificateVerifier.X509_V_ERR_UNSUPPORTED_NAME_SYNTAX;
        errors.put(X509_V_ERR_UNSUPPORTED_NAME_SYNTAX, Boolean.TRUE);

        X509_V_ERR_CRL_PATH_VALIDATION_ERROR = CertificateVerifier.X509_V_ERR_CRL_PATH_VALIDATION_ERROR;
        errors.put(X509_V_ERR_CRL_PATH_VALIDATION_ERROR, Boolean.TRUE);

        X509_V_ERR_PATH_LOOP = CertificateVerifier.X509_V_ERR_PATH_LOOP;
        errors.put(X509_V_ERR_PATH_LOOP, Boolean.TRUE);

        X509_V_ERR_SUITE_B_INVALID_VERSION = CertificateVerifier.X509_V_ERR_SUITE_B_INVALID_VERSION;
        errors.put(X509_V_ERR_SUITE_B_INVALID_VERSION, Boolean.TRUE);

        X509_V_ERR_SUITE_B_INVALID_ALGORITHM = CertificateVerifier.X509_V_ERR_SUITE_B_INVALID_ALGORITHM;
        errors.put(X509_V_ERR_SUITE_B_INVALID_ALGORITHM, Boolean.TRUE);

        X509_V_ERR_SUITE_B_INVALID_CURVE = CertificateVerifier.X509_V_ERR_SUITE_B_INVALID_CURVE;
        errors.put(X509_V_ERR_SUITE_B_INVALID_CURVE, Boolean.TRUE);

        X509_V_ERR_SUITE_B_INVALID_SIGNATURE_ALGORITHM =
                CertificateVerifier.X509_V_ERR_SUITE_B_INVALID_SIGNATURE_ALGORITHM;
        errors.put(X509_V_ERR_SUITE_B_INVALID_SIGNATURE_ALGORITHM, Boolean.TRUE);

        X509_V_ERR_SUITE_B_LOS_NOT_ALLOWED = CertificateVerifier.X509_V_ERR_SUITE_B_LOS_NOT_ALLOWED;
        errors.put(X509_V_ERR_SUITE_B_LOS_NOT_ALLOWED, Boolean.TRUE);

        X509_V_ERR_SUITE_B_CANNOT_SIGN_P_384_WITH_P_256 =
                CertificateVerifier.X509_V_ERR_SUITE_B_CANNOT_SIGN_P_384_WITH_P_256;
        errors.put(X509_V_ERR_SUITE_B_CANNOT_SIGN_P_384_WITH_P_256, Boolean.TRUE);

        X509_V_ERR_HOSTNAME_MISMATCH = CertificateVerifier.X509_V_ERR_HOSTNAME_MISMATCH;
        errors.put(X509_V_ERR_HOSTNAME_MISMATCH, Boolean.TRUE);

        X509_V_ERR_EMAIL_MISMATCH = CertificateVerifier.X509_V_ERR_EMAIL_MISMATCH;
        errors.put(X509_V_ERR_EMAIL_MISMATCH, Boolean.TRUE);

        X509_V_ERR_IP_ADDRESS_MISMATCH = CertificateVerifier.X509_V_ERR_IP_ADDRESS_MISMATCH;
        errors.put(X509_V_ERR_IP_ADDRESS_MISMATCH, Boolean.TRUE);

        X509_V_ERR_DANE_NO_MATCH = CertificateVerifier.X509_V_ERR_DANE_NO_MATCH;
        errors.put(X509_V_ERR_DANE_NO_MATCH, Boolean.TRUE);

        ERROR_MAP = IntCollections.unmodifiableMap(errors);
    }

    private static final long serialVersionUID = 5542675253797129798L;

    private final int errorCode;

    /**
     * Construct a new exception with the
     * <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a>.
     */
    public OpenSslCertificateException(int errorCode) {
        this((String) null, errorCode);
    }

    /**
     * Construct a new exception with the msg and
     * <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a> .
     */
    public OpenSslCertificateException(String msg, int errorCode) {
        super(msg);
        this.errorCode = checkErrorCode(errorCode);
    }

    /**
     * Construct a new exception with the msg, cause and
     * <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a> .
     */
    public OpenSslCertificateException(String message, Throwable cause, int errorCode) {
        super(message, cause);
        this.errorCode = checkErrorCode(errorCode);
    }

    /**
     * Construct a new exception with the cause and
     * <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a> .
     */
    public OpenSslCertificateException(Throwable cause, int errorCode) {
        this(null, cause, errorCode);
    }

    /**
     * Return the <a href="https://www.openssl.org/docs/manmaster/apps/verify.html">error code</a> to use.
     */
    public int errorCode() {
        return errorCode;
    }

    private static int checkErrorCode(int errorCode) {
        if (ERROR_MAP.get(errorCode) != Boolean.TRUE) {
            throw new IllegalArgumentException("errorCode '" + errorCode + "' invalid." +
                            " See https://www.openssl.org/docs/manmaster/apps/verify.html.");
        }
        return errorCode;
    }
}
