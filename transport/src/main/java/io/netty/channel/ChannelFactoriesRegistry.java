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
package io.netty.channel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class provides a default mapping between {@link EventLoopGroup} implementations and {@link ChannelFactory}
 * implementations
 */
public abstract class ChannelFactoriesRegistry {

    private static final class ChannelFactoryDefaults {
        private final Class<? extends EventLoopGroup> eventLoopGroupType;
        private final ChannelFactory clientSideFactory;
        private final ChannelFactory serverSideFactory;

        public ChannelFactoryDefaults(Class<? extends EventLoopGroup> eventLoopGroupType,
                                      ChannelFactory clientSideFactory, ChannelFactory serverSideFactory) {
            this.eventLoopGroupType = eventLoopGroupType;
            this.clientSideFactory = clientSideFactory;
            this.serverSideFactory = serverSideFactory;
        }

        private boolean matchesClass(Class<? extends EventLoopGroup> clazz) {
            return eventLoopGroupType.isAssignableFrom(clazz);
        }
    }

    private static final List<ChannelFactoryDefaults> DEFAULT_CHANNEL_FACTORIES
        = new ArrayList<ChannelFactoryDefaults>();

    private ChannelFactoriesRegistry() { }

    public static void registerFactoryForEventLoopGroup(Class<? extends EventLoopGroup> groupClass,
                                                        ChannelFactory factoryForClients,
                                                        ChannelFactory factoryForServers) {
        synchronized (DEFAULT_CHANNEL_FACTORIES) {
            for (Iterator<ChannelFactoryDefaults> it = DEFAULT_CHANNEL_FACTORIES.iterator(); it.hasNext();) {
                ChannelFactoryDefaults channelFactoryDefaults = it.next();
                if (channelFactoryDefaults.matchesClass(groupClass)) {
                    it.remove();
                }
            }
            DEFAULT_CHANNEL_FACTORIES.add(new ChannelFactoryDefaults(groupClass, factoryForClients, factoryForServers));
        }
    }

    public static ChannelFactory getFactoryForEventLoopGroup(Class<? extends EventLoopGroup> groupClass,
                                                             boolean serverSide) {
        synchronized (DEFAULT_CHANNEL_FACTORIES) {
            for (ChannelFactoryDefaults channelFactoryDefaults : DEFAULT_CHANNEL_FACTORIES) {
                if (channelFactoryDefaults.matchesClass(groupClass)) {
                    return serverSide ?
                        channelFactoryDefaults.serverSideFactory : channelFactoryDefaults.clientSideFactory;
                }
            }
            return null;
        }
    }
}
