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
package io.netty.resolver.dns;

import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.util.internal.UnstableApi;

import java.net.InetSocketAddress;

/**
 * A {@link DnsNameResolverException} raised when {@link DnsNameResolver} failed to perform a successful query because
 * of an timeout. In this case you may want to retry the operation.
 */
@UnstableApi
public final class DnsNameResolverTimeoutException extends DnsNameResolverException {
    private static final long serialVersionUID = -8826717969627131854L;

    public DnsNameResolverTimeoutException(
            InetSocketAddress remoteAddress, DnsQuestion question, String message) {
        super(remoteAddress, question, message);
    }
}
