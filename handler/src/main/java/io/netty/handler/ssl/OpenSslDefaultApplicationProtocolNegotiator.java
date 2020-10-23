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
package io.netty.handler.ssl;

import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * OpenSSL {@link ApplicationProtocolNegotiator} for ALPN and NPN.
 *
 * @deprecated use {@link ApplicationProtocolConfig}.
 */
@Deprecated
public final class OpenSslDefaultApplicationProtocolNegotiator implements OpenSslApplicationProtocolNegotiator {
    private final ApplicationProtocolConfig config;
    public OpenSslDefaultApplicationProtocolNegotiator(ApplicationProtocolConfig config) {
        this.config = checkNotNull(config, "config");
    }

    @Override
    public List<String> protocols() {
        return config.supportedProtocols();
    }

    @Override
    public ApplicationProtocolConfig.Protocol protocol() {
        return config.protocol();
    }

    @Override
    public ApplicationProtocolConfig.SelectorFailureBehavior selectorFailureBehavior() {
        return config.selectorFailureBehavior();
    }

    @Override
    public ApplicationProtocolConfig.SelectedListenerFailureBehavior selectedListenerFailureBehavior() {
        return config.selectedListenerFailureBehavior();
    }
}
