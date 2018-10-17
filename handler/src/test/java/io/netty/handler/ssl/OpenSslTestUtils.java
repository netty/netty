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
package io.netty.handler.ssl;

import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;
import java.util.Collections;

import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1_2;
import static org.junit.Assume.assumeTrue;

final class OpenSslTestUtils {
    private OpenSslTestUtils() {
    }

    static void checkShouldUseKeyManagerFactory() {
        assumeTrue(OpenSsl.supportsKeyManagerFactory() && OpenSsl.useKeyManagerFactory());
    }

    static boolean isBoringSSL() {
        return "BoringSSL".equals(OpenSsl.versionString());
    }

    static SslContextBuilder configureProtocolForMutualAuth(
            SslContextBuilder ctx, SslProvider sslClientProvider, SslProvider sslServerProvider) {
        if (PlatformDependent.javaVersion() >= 11
            && sslClientProvider == SslProvider.JDK && sslServerProvider != SslProvider.JDK) {
            // Make sure we do not use TLSv1.3 as there seems to be a bug currently in the JDK TLSv1.3 implementation.
            // See:
            //  - http://mail.openjdk.java.net/pipermail/security-dev/2018-September/018191.html
            //  - https://bugs.openjdk.java.net/projects/JDK/issues/JDK-8210846
            ctx.protocols(PROTOCOL_TLS_V1_2).ciphers(Collections.singleton("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"));
        }
        return ctx;
    }
}
