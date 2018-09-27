/*
 * Copyright 2018 The Netty Project
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

import io.netty.channel.EventLoop;
import io.netty.util.internal.UnstableApi;

@UnstableApi
public final class NoopDnsCnameCache implements DnsCnameCache {

    public static final NoopDnsCnameCache INSTANCE = new NoopDnsCnameCache();

    private NoopDnsCnameCache() { }

    @Override
    public String get(String hostname) {
        return null;
    }

    @Override
    public void cache(String hostname, String cname, long originalTtl, EventLoop loop) {
        // NOOP
    }

    @Override
    public void clear() {
        // NOOP
    }

    @Override
    public boolean clear(String hostname) {
        return false;
    }
}
