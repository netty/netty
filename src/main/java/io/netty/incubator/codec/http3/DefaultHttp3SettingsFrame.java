/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.collection.LongObjectMap;

import java.util.Iterator;
import java.util.Map;

public final class DefaultHttp3SettingsFrame implements Http3SettingsFrame {

    private final LongObjectMap<Long> settings = new LongObjectHashMap<>(4);

    @Override
    public Long get(long key) {
        return settings.get(key);
    }

    @Override
    public void put(long key, Long value) {
        settings.put(key, value);
    }

    @Override
    public Iterator<Map.Entry<Long, Long>> iterator() {
        return settings.entrySet().iterator();
    }
}
