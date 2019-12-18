/*
 * Copyright 2019 The Netty Project
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

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator;

import javax.net.ssl.SSLEngine;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK8OrEarlier;

@TargetClass(
    className = "io.netty.handler.ssl.JdkAlpnApplicationProtocolNegotiator$AlpnWrapper",
    onlyWith = JDK8OrEarlier.class)
final class JdkAlpnApplicationProtocolNegotiator$AlpnWrapperSubstitutionForJava8 {

    @Substitute
    public SSLEngine wrapSslEngine(SSLEngine engine, ByteBufAllocator alloc,
        JdkApplicationProtocolNegotiator applicationNegotiator, boolean isServer) {
        if (JettyAlpnSslEngineSubstitutions.isAvailable()) {
            return isServer
                ? (SSLEngine) (Object) JettyAlpnSslEngineSubstitutions.newServerEngine(engine,
                applicationNegotiator)
                : (SSLEngine) (Object) JettyAlpnSslEngineSubstitutions.newClientEngine(engine,
                applicationNegotiator);
        }
        throw new RuntimeException("Unable to wrap SSLEngine of type " + engine.getClass().getName());
    }
}
