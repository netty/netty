/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This class provides a default mapping between {@link EventLoopGroup} implementations and {@link ChannelFactory}
 * implementations
 */
public abstract class ChannelFactoriesRegistry {

    private static ConcurrentMap<Class<? extends EventLoopGroup>, ChannelFactory> clientSideFactories
        = new ConcurrentHashMap<Class<? extends EventLoopGroup>, ChannelFactory>();

    private static ConcurrentMap<Class<? extends EventLoopGroup>, ChannelFactory> serverSideFactories
        = new ConcurrentHashMap<Class<? extends EventLoopGroup>, ChannelFactory>();

    private ChannelFactoriesRegistry() { }

    public static void registerFactoryForEventLoopGroup(Class<? extends EventLoopGroup> groupClass,
        ChannelFactory factoryForClients, ChannelFactory factoryForServers) {
        clientSideFactories.put(groupClass, factoryForClients);
        serverSideFactories.put(groupClass, factoryForServers);
    }

    public static ChannelFactory getFactoryForEventLoopGroup(Class<? extends EventLoopGroup> groupClass,
        boolean serverSide) {
        return serverSide ? serverSideFactories.get(groupClass) : clientSideFactories.get(groupClass);
    }
}
