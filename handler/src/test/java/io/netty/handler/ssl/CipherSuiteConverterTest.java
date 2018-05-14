/*
 * Copyright 2014 The Netty Project
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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class CipherSuiteConverterTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CipherSuiteConverterTest.class);

    @Test
    public void testJ2OMappings() throws Exception {
        testJ2OMapping("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "ECDHE-ECDSA-AES128-SHA256");
        testJ2OMapping("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "ECDHE-RSA-AES128-SHA256");
        testJ2OMapping("TLS_RSA_WITH_AES_128_CBC_SHA256", "AES128-SHA256");
        testJ2OMapping("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256", "ECDH-ECDSA-AES128-SHA256");
        testJ2OMapping("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256", "ECDH-RSA-AES128-SHA256");
        testJ2OMapping("TLS_DHE_RSA_WITH_AES_128_CBC_SHA256", "DHE-RSA-AES128-SHA256");
        testJ2OMapping("TLS_DHE_DSS_WITH_AES_128_CBC_SHA256", "DHE-DSS-AES128-SHA256");
        testJ2OMapping("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "ECDHE-ECDSA-AES128-SHA");
        testJ2OMapping("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "ECDHE-RSA-AES128-SHA");
        testJ2OMapping("TLS_RSA_WITH_AES_128_CBC_SHA", "AES128-SHA");
        testJ2OMapping("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA", "ECDH-ECDSA-AES128-SHA");
        testJ2OMapping("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA", "ECDH-RSA-AES128-SHA");
        testJ2OMapping("TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "DHE-RSA-AES128-SHA");
        testJ2OMapping("TLS_DHE_DSS_WITH_AES_128_CBC_SHA", "DHE-DSS-AES128-SHA");
        testJ2OMapping("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "ECDHE-ECDSA-AES128-GCM-SHA256");
        testJ2OMapping("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "ECDHE-RSA-AES128-GCM-SHA256");
        testJ2OMapping("TLS_RSA_WITH_AES_128_GCM_SHA256", "AES128-GCM-SHA256");
        testJ2OMapping("TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256", "ECDH-ECDSA-AES128-GCM-SHA256");
        testJ2OMapping("TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256", "ECDH-RSA-AES128-GCM-SHA256");
        testJ2OMapping("TLS_DHE_RSA_WITH_AES_128_GCM_SHA256", "DHE-RSA-AES128-GCM-SHA256");
        testJ2OMapping("TLS_DHE_DSS_WITH_AES_128_GCM_SHA256", "DHE-DSS-AES128-GCM-SHA256");
        testJ2OMapping("TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA", "ECDHE-ECDSA-DES-CBC3-SHA");
        testJ2OMapping("TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "ECDHE-RSA-DES-CBC3-SHA");
        testJ2OMapping("SSL_RSA_WITH_3DES_EDE_CBC_SHA", "DES-CBC3-SHA");
        testJ2OMapping("TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA", "ECDH-ECDSA-DES-CBC3-SHA");
        testJ2OMapping("TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA", "ECDH-RSA-DES-CBC3-SHA");
        testJ2OMapping("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "DHE-RSA-DES-CBC3-SHA");
        testJ2OMapping("SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "DHE-DSS-DES-CBC3-SHA");
        testJ2OMapping("TLS_ECDHE_ECDSA_WITH_RC4_128_SHA", "ECDHE-ECDSA-RC4-SHA");
        testJ2OMapping("TLS_ECDHE_RSA_WITH_RC4_128_SHA", "ECDHE-RSA-RC4-SHA");
        testJ2OMapping("SSL_RSA_WITH_RC4_128_SHA", "RC4-SHA");
        testJ2OMapping("TLS_ECDH_ECDSA_WITH_RC4_128_SHA", "ECDH-ECDSA-RC4-SHA");
        testJ2OMapping("TLS_ECDH_RSA_WITH_RC4_128_SHA", "ECDH-RSA-RC4-SHA");
        testJ2OMapping("SSL_RSA_WITH_RC4_128_MD5", "RC4-MD5");
        testJ2OMapping("TLS_DH_anon_WITH_AES_128_GCM_SHA256", "ADH-AES128-GCM-SHA256");
        testJ2OMapping("TLS_DH_anon_WITH_AES_128_CBC_SHA256", "ADH-AES128-SHA256");
        testJ2OMapping("TLS_ECDH_anon_WITH_AES_128_CBC_SHA", "AECDH-AES128-SHA");
        testJ2OMapping("TLS_DH_anon_WITH_AES_128_CBC_SHA", "ADH-AES128-SHA");
        testJ2OMapping("TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA", "AECDH-DES-CBC3-SHA");
        testJ2OMapping("SSL_DH_anon_WITH_3DES_EDE_CBC_SHA", "ADH-DES-CBC3-SHA");
        testJ2OMapping("TLS_ECDH_anon_WITH_RC4_128_SHA", "AECDH-RC4-SHA");
        testJ2OMapping("SSL_DH_anon_WITH_RC4_128_MD5", "ADH-RC4-MD5");
        testJ2OMapping("SSL_RSA_WITH_DES_CBC_SHA", "DES-CBC-SHA");
        testJ2OMapping("SSL_DHE_RSA_WITH_DES_CBC_SHA", "DHE-RSA-DES-CBC-SHA");
        testJ2OMapping("SSL_DHE_DSS_WITH_DES_CBC_SHA", "DHE-DSS-DES-CBC-SHA");
        testJ2OMapping("SSL_DH_anon_WITH_DES_CBC_SHA", "ADH-DES-CBC-SHA");
        testJ2OMapping("SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", "EXP-DES-CBC-SHA");
        testJ2OMapping("SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "EXP-DHE-RSA-DES-CBC-SHA");
        testJ2OMapping("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA", "EXP-DHE-DSS-DES-CBC-SHA");
        testJ2OMapping("SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA", "EXP-ADH-DES-CBC-SHA");
        testJ2OMapping("SSL_RSA_EXPORT_WITH_RC4_40_MD5", "EXP-RC4-MD5");
        testJ2OMapping("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", "EXP-ADH-RC4-MD5");
        testJ2OMapping("TLS_RSA_WITH_NULL_SHA256", "NULL-SHA256");
        testJ2OMapping("TLS_ECDHE_ECDSA_WITH_NULL_SHA", "ECDHE-ECDSA-NULL-SHA");
        testJ2OMapping("TLS_ECDHE_RSA_WITH_NULL_SHA", "ECDHE-RSA-NULL-SHA");
        testJ2OMapping("SSL_RSA_WITH_NULL_SHA", "NULL-SHA");
        testJ2OMapping("TLS_ECDH_ECDSA_WITH_NULL_SHA", "ECDH-ECDSA-NULL-SHA");
        testJ2OMapping("TLS_ECDH_RSA_WITH_NULL_SHA", "ECDH-RSA-NULL-SHA");
        testJ2OMapping("TLS_ECDH_anon_WITH_NULL_SHA", "AECDH-NULL-SHA");
        testJ2OMapping("SSL_RSA_WITH_NULL_MD5", "NULL-MD5");
        testJ2OMapping("TLS_KRB5_WITH_3DES_EDE_CBC_SHA", "KRB5-DES-CBC3-SHA");
        testJ2OMapping("TLS_KRB5_WITH_3DES_EDE_CBC_MD5", "KRB5-DES-CBC3-MD5");
        testJ2OMapping("TLS_KRB5_WITH_RC4_128_SHA", "KRB5-RC4-SHA");
        testJ2OMapping("TLS_KRB5_WITH_RC4_128_MD5", "KRB5-RC4-MD5");
        testJ2OMapping("TLS_KRB5_WITH_DES_CBC_SHA", "KRB5-DES-CBC-SHA");
        testJ2OMapping("TLS_KRB5_WITH_DES_CBC_MD5", "KRB5-DES-CBC-MD5");
        testJ2OMapping("TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA", "EXP-KRB5-DES-CBC-SHA");
        testJ2OMapping("TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5", "EXP-KRB5-DES-CBC-MD5");
        testJ2OMapping("TLS_KRB5_EXPORT_WITH_RC4_40_SHA", "EXP-KRB5-RC4-SHA");
        testJ2OMapping("TLS_KRB5_EXPORT_WITH_RC4_40_MD5", "EXP-KRB5-RC4-MD5");
        testJ2OMapping("SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5", "EXP-RC2-CBC-MD5");
        testJ2OMapping("TLS_DHE_DSS_WITH_AES_256_CBC_SHA", "DHE-DSS-AES256-SHA");
        testJ2OMapping("TLS_DHE_RSA_WITH_AES_256_CBC_SHA", "DHE-RSA-AES256-SHA");
        testJ2OMapping("TLS_DH_anon_WITH_AES_256_CBC_SHA", "ADH-AES256-SHA");
        testJ2OMapping("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA", "ECDHE-ECDSA-AES256-SHA");
        testJ2OMapping("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA", "ECDHE-RSA-AES256-SHA");
        testJ2OMapping("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA", "ECDH-ECDSA-AES256-SHA");
        testJ2OMapping("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA", "ECDH-RSA-AES256-SHA");
        testJ2OMapping("TLS_ECDH_anon_WITH_AES_256_CBC_SHA", "AECDH-AES256-SHA");
        testJ2OMapping("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5", "EXP-KRB5-RC2-CBC-MD5");
        testJ2OMapping("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA", "EXP-KRB5-RC2-CBC-SHA");
        testJ2OMapping("TLS_RSA_WITH_AES_256_CBC_SHA", "AES256-SHA");

        // For historical reasons the CHACHA20 ciphers do not follow OpenSSL's custom naming
        // convention and omits the HMAC algorithm portion of the name.
        testJ2OMapping("TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "ECDHE-RSA-CHACHA20-POLY1305");
        testJ2OMapping("TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", "ECDHE-ECDSA-CHACHA20-POLY1305");
        testJ2OMapping("TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "DHE-RSA-CHACHA20-POLY1305");
        testJ2OMapping("TLS_PSK_WITH_CHACHA20_POLY1305_SHA256", "PSK-CHACHA20-POLY1305");
        testJ2OMapping("TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256", "ECDHE-PSK-CHACHA20-POLY1305");
        testJ2OMapping("TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256", "DHE-PSK-CHACHA20-POLY1305");
        testJ2OMapping("TLS_RSA_PSK_WITH_CHACHA20_POLY1305_SHA256", "RSA-PSK-CHACHA20-POLY1305");
    }

    private static void testJ2OMapping(String javaCipherSuite, String openSslCipherSuite) {
        final String actual = CipherSuiteConverter.toOpenSslUncached(javaCipherSuite);
        logger.info("{} => {}", javaCipherSuite, actual);
        assertThat(actual, is(openSslCipherSuite));
    }

    @Test
    public void testO2JMappings() throws Exception {
        testO2JMapping("ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "ECDHE-ECDSA-AES128-SHA256");
        testO2JMapping("ECDHE_RSA_WITH_AES_128_CBC_SHA256", "ECDHE-RSA-AES128-SHA256");
        testO2JMapping("RSA_WITH_AES_128_CBC_SHA256", "AES128-SHA256");
        testO2JMapping("ECDH_ECDSA_WITH_AES_128_CBC_SHA256", "ECDH-ECDSA-AES128-SHA256");
        testO2JMapping("ECDH_RSA_WITH_AES_128_CBC_SHA256", "ECDH-RSA-AES128-SHA256");
        testO2JMapping("DHE_RSA_WITH_AES_128_CBC_SHA256", "DHE-RSA-AES128-SHA256");
        testO2JMapping("DHE_DSS_WITH_AES_128_CBC_SHA256", "DHE-DSS-AES128-SHA256");
        testO2JMapping("ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "ECDHE-ECDSA-AES128-SHA");
        testO2JMapping("ECDHE_RSA_WITH_AES_128_CBC_SHA", "ECDHE-RSA-AES128-SHA");
        testO2JMapping("RSA_WITH_AES_128_CBC_SHA", "AES128-SHA");
        testO2JMapping("ECDH_ECDSA_WITH_AES_128_CBC_SHA", "ECDH-ECDSA-AES128-SHA");
        testO2JMapping("ECDH_RSA_WITH_AES_128_CBC_SHA", "ECDH-RSA-AES128-SHA");
        testO2JMapping("DHE_RSA_WITH_AES_128_CBC_SHA", "DHE-RSA-AES128-SHA");
        testO2JMapping("DHE_DSS_WITH_AES_128_CBC_SHA", "DHE-DSS-AES128-SHA");
        testO2JMapping("ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "ECDHE-ECDSA-AES128-GCM-SHA256");
        testO2JMapping("ECDHE_RSA_WITH_AES_128_GCM_SHA256", "ECDHE-RSA-AES128-GCM-SHA256");
        testO2JMapping("RSA_WITH_AES_128_GCM_SHA256", "AES128-GCM-SHA256");
        testO2JMapping("ECDH_ECDSA_WITH_AES_128_GCM_SHA256", "ECDH-ECDSA-AES128-GCM-SHA256");
        testO2JMapping("ECDH_RSA_WITH_AES_128_GCM_SHA256", "ECDH-RSA-AES128-GCM-SHA256");
        testO2JMapping("DHE_RSA_WITH_AES_128_GCM_SHA256", "DHE-RSA-AES128-GCM-SHA256");
        testO2JMapping("DHE_DSS_WITH_AES_128_GCM_SHA256", "DHE-DSS-AES128-GCM-SHA256");
        testO2JMapping("ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA", "ECDHE-ECDSA-DES-CBC3-SHA");
        testO2JMapping("ECDHE_RSA_WITH_3DES_EDE_CBC_SHA", "ECDHE-RSA-DES-CBC3-SHA");
        testO2JMapping("RSA_WITH_3DES_EDE_CBC_SHA", "DES-CBC3-SHA");
        testO2JMapping("ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA", "ECDH-ECDSA-DES-CBC3-SHA");
        testO2JMapping("ECDH_RSA_WITH_3DES_EDE_CBC_SHA", "ECDH-RSA-DES-CBC3-SHA");
        testO2JMapping("DHE_RSA_WITH_3DES_EDE_CBC_SHA", "DHE-RSA-DES-CBC3-SHA");
        testO2JMapping("DHE_DSS_WITH_3DES_EDE_CBC_SHA", "DHE-DSS-DES-CBC3-SHA");
        testO2JMapping("ECDHE_ECDSA_WITH_RC4_128_SHA", "ECDHE-ECDSA-RC4-SHA");
        testO2JMapping("ECDHE_RSA_WITH_RC4_128_SHA", "ECDHE-RSA-RC4-SHA");
        testO2JMapping("RSA_WITH_RC4_128_SHA", "RC4-SHA");
        testO2JMapping("ECDH_ECDSA_WITH_RC4_128_SHA", "ECDH-ECDSA-RC4-SHA");
        testO2JMapping("ECDH_RSA_WITH_RC4_128_SHA", "ECDH-RSA-RC4-SHA");
        testO2JMapping("RSA_WITH_RC4_128_MD5", "RC4-MD5");
        testO2JMapping("DH_anon_WITH_AES_128_GCM_SHA256", "ADH-AES128-GCM-SHA256");
        testO2JMapping("DH_anon_WITH_AES_128_CBC_SHA256", "ADH-AES128-SHA256");
        testO2JMapping("ECDH_anon_WITH_AES_128_CBC_SHA", "AECDH-AES128-SHA");
        testO2JMapping("DH_anon_WITH_AES_128_CBC_SHA", "ADH-AES128-SHA");
        testO2JMapping("ECDH_anon_WITH_3DES_EDE_CBC_SHA", "AECDH-DES-CBC3-SHA");
        testO2JMapping("DH_anon_WITH_3DES_EDE_CBC_SHA", "ADH-DES-CBC3-SHA");
        testO2JMapping("ECDH_anon_WITH_RC4_128_SHA", "AECDH-RC4-SHA");
        testO2JMapping("DH_anon_WITH_RC4_128_MD5", "ADH-RC4-MD5");
        testO2JMapping("RSA_WITH_DES_CBC_SHA", "DES-CBC-SHA");
        testO2JMapping("DHE_RSA_WITH_DES_CBC_SHA", "DHE-RSA-DES-CBC-SHA");
        testO2JMapping("DHE_DSS_WITH_DES_CBC_SHA", "DHE-DSS-DES-CBC-SHA");
        testO2JMapping("DH_anon_WITH_DES_CBC_SHA", "ADH-DES-CBC-SHA");
        testO2JMapping("RSA_EXPORT_WITH_DES_CBC_40_SHA", "EXP-DES-CBC-SHA");
        testO2JMapping("DHE_RSA_EXPORT_WITH_DES_CBC_40_SHA", "EXP-DHE-RSA-DES-CBC-SHA");
        testO2JMapping("DHE_DSS_EXPORT_WITH_DES_CBC_40_SHA", "EXP-DHE-DSS-DES-CBC-SHA");
        testO2JMapping("DH_anon_EXPORT_WITH_DES_CBC_40_SHA", "EXP-ADH-DES-CBC-SHA");
        testO2JMapping("RSA_EXPORT_WITH_RC4_40_MD5", "EXP-RC4-MD5");
        testO2JMapping("DH_anon_EXPORT_WITH_RC4_40_MD5", "EXP-ADH-RC4-MD5");
        testO2JMapping("RSA_WITH_NULL_SHA256", "NULL-SHA256");
        testO2JMapping("ECDHE_ECDSA_WITH_NULL_SHA", "ECDHE-ECDSA-NULL-SHA");
        testO2JMapping("ECDHE_RSA_WITH_NULL_SHA", "ECDHE-RSA-NULL-SHA");
        testO2JMapping("RSA_WITH_NULL_SHA", "NULL-SHA");
        testO2JMapping("ECDH_ECDSA_WITH_NULL_SHA", "ECDH-ECDSA-NULL-SHA");
        testO2JMapping("ECDH_RSA_WITH_NULL_SHA", "ECDH-RSA-NULL-SHA");
        testO2JMapping("ECDH_anon_WITH_NULL_SHA", "AECDH-NULL-SHA");
        testO2JMapping("RSA_WITH_NULL_MD5", "NULL-MD5");
        testO2JMapping("KRB5_WITH_3DES_EDE_CBC_SHA", "KRB5-DES-CBC3-SHA");
        testO2JMapping("KRB5_WITH_3DES_EDE_CBC_MD5", "KRB5-DES-CBC3-MD5");
        testO2JMapping("KRB5_WITH_RC4_128_SHA", "KRB5-RC4-SHA");
        testO2JMapping("KRB5_WITH_RC4_128_MD5", "KRB5-RC4-MD5");
        testO2JMapping("KRB5_WITH_DES_CBC_SHA", "KRB5-DES-CBC-SHA");
        testO2JMapping("KRB5_WITH_DES_CBC_MD5", "KRB5-DES-CBC-MD5");
        testO2JMapping("KRB5_EXPORT_WITH_DES_CBC_40_SHA", "EXP-KRB5-DES-CBC-SHA");
        testO2JMapping("KRB5_EXPORT_WITH_DES_CBC_40_MD5", "EXP-KRB5-DES-CBC-MD5");
        testO2JMapping("KRB5_EXPORT_WITH_RC4_40_SHA", "EXP-KRB5-RC4-SHA");
        testO2JMapping("KRB5_EXPORT_WITH_RC4_40_MD5", "EXP-KRB5-RC4-MD5");
        testO2JMapping("RSA_EXPORT_WITH_RC2_CBC_40_MD5", "EXP-RC2-CBC-MD5");
        testO2JMapping("DHE_DSS_WITH_AES_256_CBC_SHA", "DHE-DSS-AES256-SHA");
        testO2JMapping("DHE_RSA_WITH_AES_256_CBC_SHA", "DHE-RSA-AES256-SHA");
        testO2JMapping("DH_anon_WITH_AES_256_CBC_SHA", "ADH-AES256-SHA");
        testO2JMapping("ECDHE_ECDSA_WITH_AES_256_CBC_SHA", "ECDHE-ECDSA-AES256-SHA");
        testO2JMapping("ECDHE_RSA_WITH_AES_256_CBC_SHA", "ECDHE-RSA-AES256-SHA");
        testO2JMapping("ECDH_ECDSA_WITH_AES_256_CBC_SHA", "ECDH-ECDSA-AES256-SHA");
        testO2JMapping("ECDH_RSA_WITH_AES_256_CBC_SHA", "ECDH-RSA-AES256-SHA");
        testO2JMapping("ECDH_anon_WITH_AES_256_CBC_SHA", "AECDH-AES256-SHA");
        testO2JMapping("KRB5_EXPORT_WITH_RC2_CBC_40_MD5", "EXP-KRB5-RC2-CBC-MD5");
        testO2JMapping("KRB5_EXPORT_WITH_RC2_CBC_40_SHA", "EXP-KRB5-RC2-CBC-SHA");
        testO2JMapping("RSA_WITH_AES_256_CBC_SHA", "AES256-SHA");

        // Test the known mappings that actually do not exist in Java
        testO2JMapping("EDH_DSS_WITH_3DES_EDE_CBC_SHA", "EDH-DSS-DES-CBC3-SHA");
        testO2JMapping("RSA_WITH_SEED_SHA", "SEED-SHA");
        testO2JMapping("RSA_WITH_CAMELLIA128_SHA", "CAMELLIA128-SHA");
        testO2JMapping("RSA_WITH_IDEA_CBC_SHA", "IDEA-CBC-SHA");
        testO2JMapping("PSK_WITH_AES_128_CBC_SHA", "PSK-AES128-CBC-SHA");
        testO2JMapping("PSK_WITH_3DES_EDE_CBC_SHA", "PSK-3DES-EDE-CBC-SHA");
        testO2JMapping("KRB5_WITH_IDEA_CBC_SHA", "KRB5-IDEA-CBC-SHA");
        testO2JMapping("KRB5_WITH_IDEA_CBC_MD5", "KRB5-IDEA-CBC-MD5");
        testO2JMapping("PSK_WITH_RC4_128_SHA", "PSK-RC4-SHA");
        testO2JMapping("ECDHE_RSA_WITH_AES_256_GCM_SHA384", "ECDHE-RSA-AES256-GCM-SHA384");
        testO2JMapping("ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", "ECDHE-ECDSA-AES256-GCM-SHA384");
        testO2JMapping("ECDHE_RSA_WITH_AES_256_CBC_SHA384", "ECDHE-RSA-AES256-SHA384");
        testO2JMapping("ECDHE_ECDSA_WITH_AES_256_CBC_SHA384", "ECDHE-ECDSA-AES256-SHA384");
        testO2JMapping("DHE_DSS_WITH_AES_256_GCM_SHA384", "DHE-DSS-AES256-GCM-SHA384");
        testO2JMapping("DHE_RSA_WITH_AES_256_GCM_SHA384", "DHE-RSA-AES256-GCM-SHA384");
        testO2JMapping("DHE_RSA_WITH_AES_256_CBC_SHA256", "DHE-RSA-AES256-SHA256");
        testO2JMapping("DHE_DSS_WITH_AES_256_CBC_SHA256", "DHE-DSS-AES256-SHA256");
        testO2JMapping("DHE_RSA_WITH_CAMELLIA256_SHA", "DHE-RSA-CAMELLIA256-SHA");
        testO2JMapping("DHE_DSS_WITH_CAMELLIA256_SHA", "DHE-DSS-CAMELLIA256-SHA");
        testO2JMapping("ECDH_RSA_WITH_AES_256_GCM_SHA384", "ECDH-RSA-AES256-GCM-SHA384");
        testO2JMapping("ECDH_ECDSA_WITH_AES_256_GCM_SHA384", "ECDH-ECDSA-AES256-GCM-SHA384");
        testO2JMapping("ECDH_RSA_WITH_AES_256_CBC_SHA384", "ECDH-RSA-AES256-SHA384");
        testO2JMapping("ECDH_ECDSA_WITH_AES_256_CBC_SHA384", "ECDH-ECDSA-AES256-SHA384");
        testO2JMapping("RSA_WITH_AES_256_GCM_SHA384", "AES256-GCM-SHA384");
        testO2JMapping("RSA_WITH_AES_256_CBC_SHA256", "AES256-SHA256");
        testO2JMapping("RSA_WITH_CAMELLIA256_SHA", "CAMELLIA256-SHA");
        testO2JMapping("PSK_WITH_AES_256_CBC_SHA", "PSK-AES256-CBC-SHA");
        testO2JMapping("DHE_RSA_WITH_SEED_SHA", "DHE-RSA-SEED-SHA");
        testO2JMapping("DHE_DSS_WITH_SEED_SHA", "DHE-DSS-SEED-SHA");
        testO2JMapping("DHE_RSA_WITH_CAMELLIA128_SHA", "DHE-RSA-CAMELLIA128-SHA");
        testO2JMapping("DHE_DSS_WITH_CAMELLIA128_SHA", "DHE-DSS-CAMELLIA128-SHA");
        testO2JMapping("EDH_RSA_WITH_3DES_EDE_CBC_SHA", "EDH-RSA-DES-CBC3-SHA");
        testO2JMapping("SRP_DSS_WITH_AES_256_CBC_SHA", "SRP-DSS-AES-256-CBC-SHA");
        testO2JMapping("SRP_RSA_WITH_AES_256_CBC_SHA", "SRP-RSA-AES-256-CBC-SHA");
        testO2JMapping("SRP_WITH_AES_256_CBC_SHA", "SRP-AES-256-CBC-SHA");
        testO2JMapping("DH_anon_WITH_AES_256_GCM_SHA384", "ADH-AES256-GCM-SHA384");
        testO2JMapping("DH_anon_WITH_AES_256_CBC_SHA256", "ADH-AES256-SHA256");
        testO2JMapping("DH_anon_WITH_CAMELLIA256_SHA", "ADH-CAMELLIA256-SHA");
        testO2JMapping("SRP_DSS_WITH_AES_128_CBC_SHA", "SRP-DSS-AES-128-CBC-SHA");
        testO2JMapping("SRP_RSA_WITH_AES_128_CBC_SHA", "SRP-RSA-AES-128-CBC-SHA");
        testO2JMapping("SRP_WITH_AES_128_CBC_SHA", "SRP-AES-128-CBC-SHA");
        testO2JMapping("DH_anon_WITH_SEED_SHA", "ADH-SEED-SHA");
        testO2JMapping("DH_anon_WITH_CAMELLIA128_SHA", "ADH-CAMELLIA128-SHA");
        testO2JMapping("RSA_WITH_RC2_CBC_MD5", "RC2-CBC-MD5");
        testO2JMapping("SRP_DSS_WITH_3DES_EDE_CBC_SHA", "SRP-DSS-3DES-EDE-CBC-SHA");
        testO2JMapping("SRP_RSA_WITH_3DES_EDE_CBC_SHA", "SRP-RSA-3DES-EDE-CBC-SHA");
        testO2JMapping("SRP_WITH_3DES_EDE_CBC_SHA", "SRP-3DES-EDE-CBC-SHA");
        testO2JMapping("RSA_WITH_3DES_EDE_CBC_MD5", "DES-CBC3-MD5");
        testO2JMapping("EDH_RSA_WITH_DES_CBC_SHA", "EDH-RSA-DES-CBC-SHA");
        testO2JMapping("EDH_DSS_WITH_DES_CBC_SHA", "EDH-DSS-DES-CBC-SHA");
        testO2JMapping("RSA_WITH_DES_CBC_MD5", "DES-CBC-MD5");
        testO2JMapping("EDH_RSA_EXPORT_WITH_DES_CBC_40_SHA", "EXP-EDH-RSA-DES-CBC-SHA");
        testO2JMapping("EDH_DSS_EXPORT_WITH_DES_CBC_40_SHA", "EXP-EDH-DSS-DES-CBC-SHA");

        // For historical reasons the CHACHA20 ciphers do not follow OpenSSL's custom naming
        // convention and omits the HMAC algorithm portion of the name.
        testO2JMapping("ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "ECDHE-RSA-CHACHA20-POLY1305");
        testO2JMapping("ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", "ECDHE-ECDSA-CHACHA20-POLY1305");
        testO2JMapping("DHE_RSA_WITH_CHACHA20_POLY1305_SHA256", "DHE-RSA-CHACHA20-POLY1305");
        testO2JMapping("PSK_WITH_CHACHA20_POLY1305_SHA256", "PSK-CHACHA20-POLY1305");
        testO2JMapping("ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256", "ECDHE-PSK-CHACHA20-POLY1305");
        testO2JMapping("DHE_PSK_WITH_CHACHA20_POLY1305_SHA256", "DHE-PSK-CHACHA20-POLY1305");
        testO2JMapping("RSA_PSK_WITH_CHACHA20_POLY1305_SHA256", "RSA-PSK-CHACHA20-POLY1305");
    }

    private static void testO2JMapping(String javaCipherSuite, String openSslCipherSuite) {
        final String actual = CipherSuiteConverter.toJavaUncached(openSslCipherSuite);
        logger.info("{} => {}", openSslCipherSuite, actual);
        assertThat(actual, is(javaCipherSuite));
    }

    @Test
    public void testCachedJ2OMappings() {
        testCachedJ2OMapping("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "ECDHE-ECDSA-AES128-SHA256");
    }

    @Test
    public void testUnknownOpenSSLCiphersToJava() {
        testUnknownOpenSSLCiphersToJava("(NONE)");
        testUnknownOpenSSLCiphersToJava("unknown");
        testUnknownOpenSSLCiphersToJava("");
    }

    @Test
    public void testUnknownJavaCiphersToOpenSSL() {
        testUnknownJavaCiphersToOpenSSL("(NONE)");
        testUnknownJavaCiphersToOpenSSL("unknown");
        testUnknownJavaCiphersToOpenSSL("");
    }

    private static void testUnknownOpenSSLCiphersToJava(String openSslCipherSuite) {
        CipherSuiteConverter.clearCache();

        assertNull(CipherSuiteConverter.toJava(openSslCipherSuite, "TLS"));
        assertNull(CipherSuiteConverter.toJava(openSslCipherSuite, "SSL"));
    }

    private static void testUnknownJavaCiphersToOpenSSL(String javaCipherSuite) {
        CipherSuiteConverter.clearCache();

        assertNull(CipherSuiteConverter.toOpenSsl(javaCipherSuite));
        assertNull(CipherSuiteConverter.toOpenSsl(javaCipherSuite));
    }

    private static void testCachedJ2OMapping(String javaCipherSuite, String openSslCipherSuite) {
        CipherSuiteConverter.clearCache();

        final String actual1 = CipherSuiteConverter.toOpenSsl(javaCipherSuite);
        assertThat(actual1, is(openSslCipherSuite));

        // Ensure that the cache entries have been created.
        assertThat(CipherSuiteConverter.isJ2OCached(javaCipherSuite, actual1), is(true));
        assertThat(CipherSuiteConverter.isO2JCached(actual1, "", javaCipherSuite.substring(4)), is(true));
        assertThat(CipherSuiteConverter.isO2JCached(actual1, "SSL", "SSL_" + javaCipherSuite.substring(4)), is(true));
        assertThat(CipherSuiteConverter.isO2JCached(actual1, "TLS", "TLS_" + javaCipherSuite.substring(4)), is(true));

        final String actual2 = CipherSuiteConverter.toOpenSsl(javaCipherSuite);
        assertThat(actual2, is(openSslCipherSuite));

        // Test if the returned cipher strings are identical,
        // so that the TLS sessions with the same cipher suite do not create many strings.
        assertThat(actual1, is(sameInstance(actual2)));
    }

    @Test
    public void testCachedO2JMappings() {
        testCachedO2JMapping("ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "ECDHE-ECDSA-AES128-SHA256");
    }

    private static void testCachedO2JMapping(String javaCipherSuite, String openSslCipherSuite) {
        CipherSuiteConverter.clearCache();

        final String tlsExpected = "TLS_" + javaCipherSuite;
        final String sslExpected = "SSL_" + javaCipherSuite;

        final String tlsActual1 = CipherSuiteConverter.toJava(openSslCipherSuite, "TLS");
        final String sslActual1 = CipherSuiteConverter.toJava(openSslCipherSuite, "SSL");
        assertThat(tlsActual1, is(tlsExpected));
        assertThat(sslActual1, is(sslExpected));

        // Ensure that the cache entries have been created.
        assertThat(CipherSuiteConverter.isO2JCached(openSslCipherSuite, "", javaCipherSuite), is(true));
        assertThat(CipherSuiteConverter.isO2JCached(openSslCipherSuite, "SSL", sslExpected), is(true));
        assertThat(CipherSuiteConverter.isO2JCached(openSslCipherSuite, "TLS", tlsExpected), is(true));
        assertThat(CipherSuiteConverter.isJ2OCached(tlsExpected, openSslCipherSuite), is(true));
        assertThat(CipherSuiteConverter.isJ2OCached(sslExpected, openSslCipherSuite), is(true));

        final String tlsActual2 = CipherSuiteConverter.toJava(openSslCipherSuite, "TLS");
        final String sslActual2 = CipherSuiteConverter.toJava(openSslCipherSuite, "SSL");
        assertThat(tlsActual2, is(tlsExpected));
        assertThat(sslActual2, is(sslExpected));

        // Test if the returned cipher strings are identical,
        // so that the TLS sessions with the same cipher suite do not create many strings.
        assertThat(tlsActual1, is(sameInstance(tlsActual2)));
        assertThat(sslActual1, is(sameInstance(sslActual2)));
    }
}
