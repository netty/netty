/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.internal.svm.ssl;

import io.netty.handler.ssl.JdkApplicationProtocolNegotiator;

import javax.net.ssl.SSLEngine;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK8OrEarlier;

@TargetClass(className = "io.netty.handler.ssl.JettyAlpnSslEngine", onlyWith = JDK8OrEarlier.class)
final class JettyAlpnSslEngineSubstitution {

    private JettyAlpnSslEngineSubstitution() {
    }

    /**
     * The engine is not available in a native Graal image, so we always return false and therefor in both
     * {@link #newClientEngine(SSLEngine, JdkApplicationProtocolNegotiator)} and
     * {@link #newServerEngine(SSLEngine, JdkApplicationProtocolNegotiator)}
     * {@literal null}
     * @return always false.
     */
    @SuppressWarnings("deprecation")
    @Alias
    static boolean isAvailable() {
        return false;
    }

    @Alias
    static JettyAlpnSslEngineSubstitution newClientEngine(SSLEngine engine,
        JdkApplicationProtocolNegotiator applicationNegotiator) {
        return null;
    }

    @Alias
    static JettyAlpnSslEngineSubstitution newServerEngine(SSLEngine engine,
        JdkApplicationProtocolNegotiator applicationNegotiator) {
        return null;
    }
}
