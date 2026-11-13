/*
 * Copyright 2018 The Netty Project
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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts OpenSSL signature Algorithm names to
 * <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Signature">
 *     Java signature Algorithm names</a>.
 */
final class SignatureAlgorithmConverter {

    private SignatureAlgorithmConverter() { }

    // OpenSSL has 3 different formats it uses at the moment we will match against all of these.
    // For example:
    //              ecdsa-with-SHA384
    //              hmacWithSHA384
    //              dsa_with_SHA224
    //
    // For more details see https://github.com/openssl/openssl/blob/OpenSSL_1_0_2p/crypto/objects/obj_dat.h
    //
    // BoringSSL uses a different format:
    // https://github.com/google/boringssl/blob/8525ff3/ssl/ssl_privkey.cc#L436
    //
    private static final Pattern PATTERN = Pattern.compile(
            // group 1 - 2
            "(?:(^[a-zA-Z].+)With(.+)Encryption$)|" +
            // group 3 - 4
            "(?:(^[a-zA-Z].+)(?:_with_|-with-|_pkcs1_|_pss_rsae_)(.+$))|" +
            // group 5 - 6
            "(?:(^[a-zA-Z].+)_(.+$))");

    /**
     * Converts an OpenSSL algorithm name to a Java algorithm name and return it,
     * or return {@code null} if the conversion failed because the format is not known.
     */
    static String toJavaName(String opensslName) {
        if (opensslName == null) {
            return null;
        }
        // The names the native libraries report for the signature algorithms that TLS peers offer in practice.
        // These are converted on every handshake, so they must not pay for the regular expression. Each result is
        // what the regular expression produces for that name, anything else still goes through it.
        switch (opensslName) {
            // BoringSSL and AWS-LC, see SSL_get_signature_algorithm_name(...).
            case "ecdsa_sha256":
            // OpenSSL, see OBJ_nid2ln(...).
            case "ecdsa-with-SHA256":
                return "SHA256withECDSA";
            case "ecdsa_sha384":
            case "ecdsa-with-SHA384":
                return "SHA384withECDSA";
            case "ecdsa_sha512":
            case "ecdsa-with-SHA512":
                return "SHA512withECDSA";
            case "ecdsa-with-SHA224":
                return "SHA224withECDSA";
            case "ecdsa_sha1":
            case "ecdsa-with-SHA1":
                return "SHA1withECDSA";
            case "rsa_pss_rsae_sha256":
            case "rsa_pkcs1_sha256":
            case "sha256WithRSAEncryption":
                return "SHA256withRSA";
            case "rsa_pss_rsae_sha384":
            case "rsa_pkcs1_sha384":
            case "sha384WithRSAEncryption":
                return "SHA384withRSA";
            case "rsa_pss_rsae_sha512":
            case "rsa_pkcs1_sha512":
            case "sha512WithRSAEncryption":
                return "SHA512withRSA";
            case "sha224WithRSAEncryption":
                return "SHA224withRSA";
            case "rsa_pkcs1_sha1":
            case "sha1WithRSAEncryption":
                return "SHA1withRSA";
            case "dsa_with_SHA256":
                return "SHA256withDSA";
            case "ed25519":
            case "ED25519":
                return "Ed25519";
            case "ed448":
            case "ED448":
                return "Ed448";
            // OpenSSL reports these for RSA-PSS, DSA and algorithms it has no name for. There is no Java name
            // for them.
            case "rsassaPss":
            case "dsaEncryption":
            case "undefined":
                return null;
            default:
                return toJavaNameWithPattern(opensslName);
        }
    }

    static String toJavaNameWithPattern(String opensslName) {
        // EdDSA names carry no hash, so the pattern can't match them
        if ("ed25519".equalsIgnoreCase(opensslName)) {
            return "Ed25519";
        }
        if ("ed448".equalsIgnoreCase(opensslName)) {
            return "Ed448";
        }
        Matcher matcher = PATTERN.matcher(opensslName);
        if (matcher.matches()) {
            String group1 = matcher.group(1);
            if (group1 != null) {
                return group1.toUpperCase(Locale.ROOT) + "with" + matcher.group(2).toUpperCase(Locale.ROOT);
            }
            if (matcher.group(3) != null) {
                return matcher.group(4).toUpperCase(Locale.ROOT) + "with" + matcher.group(3).toUpperCase(Locale.ROOT);
            }

            if (matcher.group(5) != null) {
                return matcher.group(6).toUpperCase(Locale.ROOT) + "with" + matcher.group(5).toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }
}
