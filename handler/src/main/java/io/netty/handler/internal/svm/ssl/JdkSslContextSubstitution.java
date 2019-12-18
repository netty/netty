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

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.JdkAlpnApplicationProtocolNegotiator;
import io.netty.handler.ssl.JdkApplicationProtocolNegotiator;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "io.netty.handler.ssl.JdkSslContext") final class JdkSslContextSubstitution {

    private JdkSslContextSubstitution() {
    }

    @Substitute
    static JdkApplicationProtocolNegotiator toNegotiator(ApplicationProtocolConfig config, boolean isServer) {
        if (config == null) {
            return (JdkApplicationProtocolNegotiator) (Object)
                JdkDefaultApplicationProtocolNegotiatorSubstitution.INSTANCE;
        }

        switch (config.protocol()) {
            case NONE:
                return (JdkApplicationProtocolNegotiator) (Object)
                    JdkDefaultApplicationProtocolNegotiatorSubstitution.INSTANCE;
            case ALPN:
                if (isServer) {
                    // Needs to be an if/else construct, see https://github.com/oracle/graal/issues/813
                    ApplicationProtocolConfig.SelectorFailureBehavior behavior = config.selectorFailureBehavior();
                    if (behavior == ApplicationProtocolConfig.SelectorFailureBehavior.FATAL_ALERT) {
                        return new JdkAlpnApplicationProtocolNegotiator(true, config.supportedProtocols());
                    } else if (behavior == ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE) {
                        return new JdkAlpnApplicationProtocolNegotiator(false, config.supportedProtocols());
                    } else {
                        throw new UnsupportedOperationException(new StringBuilder("JDK provider does not support ")
                            .append(config.selectorFailureBehavior()).append(" failure behavior").toString());
                    }
                } else {
                    switch (config.selectedListenerFailureBehavior()) {
                        case ACCEPT:
                            return new JdkAlpnApplicationProtocolNegotiator(false, config.supportedProtocols());
                        case FATAL_ALERT:
                            return new JdkAlpnApplicationProtocolNegotiator(true, config.supportedProtocols());
                        default:
                            throw new UnsupportedOperationException(new StringBuilder("JDK provider does not support ")
                                .append(config.selectedListenerFailureBehavior()).append(" failure behavior")
                                .toString());
                    }
                }
            default:
                throw new UnsupportedOperationException(
                    new StringBuilder("JDK provider does not support ").append(config.protocol()).append(" protocol")
                        .toString());
        }
    }
}
