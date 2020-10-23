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

import static io.netty.handler.ssl.ApplicationProtocolUtil.toList;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import java.util.List;

/**
 * OpenSSL {@link ApplicationProtocolNegotiator} for NPN.
 *
 * @deprecated use {@link ApplicationProtocolConfig}
 */
@Deprecated
public final class OpenSslNpnApplicationProtocolNegotiator implements OpenSslApplicationProtocolNegotiator {
    private final List<String> protocols;

    public OpenSslNpnApplicationProtocolNegotiator(Iterable<String> protocols) {
        this.protocols = checkNotNull(toList(protocols), "protocols");
    }

    public OpenSslNpnApplicationProtocolNegotiator(String... protocols) {
        this.protocols = checkNotNull(toList(protocols), "protocols");
    }

    @Override
    public ApplicationProtocolConfig.Protocol protocol() {
        return ApplicationProtocolConfig.Protocol.NPN;
    }

    @Override
    public List<String> protocols() {
        return protocols;
    }

    @Override
    public ApplicationProtocolConfig.SelectorFailureBehavior selectorFailureBehavior() {
        return ApplicationProtocolConfig.SelectorFailureBehavior.CHOOSE_MY_LAST_PROTOCOL;
    }

    @Override
    public ApplicationProtocolConfig.SelectedListenerFailureBehavior selectedListenerFailureBehavior() {
        return ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT;
    }
}
