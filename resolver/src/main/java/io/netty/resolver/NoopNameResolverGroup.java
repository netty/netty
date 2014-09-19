/*
 * Copyright 2014 The Netty Project
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

package io.netty.resolver;

import io.netty.util.concurrent.EventExecutor;

import java.net.SocketAddress;

/**
 * A {@link NameResolverGroup} of {@link NoopNameResolver}s.
 */
public final class NoopNameResolverGroup extends NameResolverGroup<SocketAddress> {

    public static final NoopNameResolverGroup INSTANCE = new NoopNameResolverGroup();

    private NoopNameResolverGroup() { }

    @Override
    protected NameResolver<SocketAddress> newResolver(EventExecutor executor) throws Exception {
        return new NoopNameResolver(executor);
    }
}
