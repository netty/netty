/*
 * Copyright 2016 The Netty Project
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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.resolver.AddressResolverGroup;

import java.net.SocketAddress;

/**
 * Exposes the configuration of a {@link Bootstrap}.
 */
public final class BootstrapConfig extends AbstractBootstrapConfig<Bootstrap, Channel> {

    BootstrapConfig(Bootstrap bootstrap) {
        super(bootstrap);
    }

    /**
     * Returns the configured remote address or {@code null} if non is configured yet.
     */
    public SocketAddress remoteAddress() {
        return bootstrap.remoteAddress();
    }

    /**
     * Returns the configured {@link AddressResolverGroup} or the default if non is configured yet.
     */
    public AddressResolverGroup<?> resolver() {
        return bootstrap.resolver();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.setLength(buf.length() - 1);
        buf.append(", resolver: ").append(resolver());
        SocketAddress remoteAddress = remoteAddress();
        if (remoteAddress != null) {
            buf.append(", remoteAddress: ")
                    .append(remoteAddress);
        }
        return buf.append(')').toString();
    }
}
