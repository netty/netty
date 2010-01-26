/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.container.guice;

import java.util.concurrent.Executor;

import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * A {@link Provider} that creates a new {@link NioClientSocketChannelFactory}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 */
public class NioClientSocketChannelFactoryProvider extends
        AbstractChannelFactoryProvider<NioClientSocketChannelFactory> {

    /**
     * Creates a new provider with the {@code executor} injected via the
     * {@link ChannelFactoryResource} annotation.
     */
    @Inject
    public NioClientSocketChannelFactoryProvider(
            @ChannelFactoryResource Executor executor) {
        super(executor);
    }

    public NioClientSocketChannelFactory get() {
        return new NioClientSocketChannelFactory(executor, executor);
    }
}
