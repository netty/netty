/*
 * Copyright 2024 The Netty Project
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

import java.security.cert.CertificateException;

public final class CachedSelfSignedCertificate {

    private CachedSelfSignedCertificate() {
    }

    /**
     * Obtain a lazily-created, shared {@link SelfSignedCertificate} instance.
     * @return A shared {@link SelfSignedCertificate}.
     */
    public static SelfSignedCertificate getCachedCertificate() {
        Object instance = LazyDefaultInstance.INSTANCE;
        if (instance instanceof SelfSignedCertificate) {
            return (SelfSignedCertificate) instance;
        }
        Throwable throwable = (Throwable) instance;
        throw new IllegalStateException("Could not create default self-signed certificate instance", throwable);
    }

    private static final class LazyDefaultInstance {
        public static final Object INSTANCE = createInstance();

        private static Object createInstance() {
            try {
                return new SelfSignedCertificate();
            } catch (CertificateException e) {
                return e;
            }
        }
    }
}
