/*
 * Copyright 2018 The Netty Project
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
    // https://github.com/google/boringssl/blob/master/ssl/ssl_privkey.cc#L436
    //
    private static final Pattern PATTERN = Pattern.compile(
            // group 1 - 3
            "((^[a-zA-Z].+)With(.+)Encryption$)|" +
            // group 4 - 7
            "((^[a-zA-Z].+)(_with_|-with-)(.+$))|" +
            // group 8 - 11
            "((^[a-zA-Z].+)(_pkcs1_|_pss_rsae_)(.+$))|" +
            // group 12 - 14
            "((^[a-zA-Z].+)_(.+$))");

    /**
     * Converts an OpenSSL algorithm name to a Java algorithm name and return it,
     * or return {@code null} if the conversation failed because the format is not known.
     */
    static String toJavaName(String opensslName) {
        if (opensslName == null) {
            return null;
        }
        Matcher matcher = PATTERN.matcher(opensslName);
        if (matcher.matches()) {
            String group2 = matcher.group(2);
            if (group2 != null) {
                return group2.toUpperCase(Locale.ROOT) + "with" + matcher.group(3).toUpperCase(Locale.ROOT);
            }
            if (matcher.group(4) != null) {
                return matcher.group(7).toUpperCase(Locale.ROOT) + "with" + matcher.group(5).toUpperCase(Locale.ROOT);
            }

            if (matcher.group(8) != null) {
                return matcher.group(11).toUpperCase(Locale.ROOT) + "with" + matcher.group(9).toUpperCase(Locale.ROOT);
            }

            if (matcher.group(8) != null) {
                return matcher.group(10).toUpperCase(Locale.ROOT) + "with" + matcher.group(5).toUpperCase(Locale.ROOT);
            }

            if (matcher.group(12) != null) {
                return matcher.group(14).toUpperCase(Locale.ROOT) + "with" + matcher.group(13).toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }
}
