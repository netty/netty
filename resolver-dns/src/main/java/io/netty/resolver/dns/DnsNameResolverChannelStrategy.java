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
package io.netty.resolver.dns;

/**
 * Strategy that influence how {@link io.netty.channel.Channel}s are used during queries.
 */
public enum DnsNameResolverChannelStrategy {
    /**
     * Use the same underlying {@link io.netty.channel.Channel} for all queries produced by a single
     {@link DnsNameResolver} instance.
     */
    ChannelPerResolver,
    /**
     * Use a new {@link io.netty.channel.Channel} per resolution or per explicit query. As of today this is similar
     * to what the {@link io.netty.resolver.DefaultNameResolver} (JDK default) does. As we will need to open and close
     * a new socket for each resolution it will come with a performance overhead. That said using this strategy should
     * be the most robust and also guard against problems that can arise in kubernetes (or similar) setups.
     */
    ChannelPerResolution
}
