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
import io.netty.util.internal.StringUtil;

import java.util.Iterator;
import java.util.Map;

public final class DefaultHttp3SettingsFrame implements Http3SettingsFrame {

    private final LongObjectMap<Long> settings = new LongObjectHashMap<>(4);

    @Override
    public Long get(long key) {
        return settings.get(key);
    }

    @Override
    public Long put(long key, Long value) {
        if (Http3CodecUtils.isReservedHttp2Setting(key)) {
            throw new IllegalArgumentException("Setting is reserved for HTTP/2: " + key);
        }
        return settings.put(key, value);
    }

    @Override
    public Iterator<Map.Entry<Long, Long>> iterator() {
        return settings.entrySet().iterator();
    }

    @Override
    public int hashCode() {
        return settings.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultHttp3SettingsFrame that = (DefaultHttp3SettingsFrame) o;
        return that.settings.equals(settings);
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(settings=" + settings + ')';
    }

    /**
     * Creates a new {@link DefaultHttp3SettingsFrame} which is a copy of the given settings.
     *
     * @param settingsFrame the frame to copy.
     * @return              the newly created copy.
     */
    public static DefaultHttp3SettingsFrame copyOf(Http3SettingsFrame settingsFrame) {
        DefaultHttp3SettingsFrame copy = new DefaultHttp3SettingsFrame();
        if (settingsFrame instanceof DefaultHttp3SettingsFrame) {
            copy.settings.putAll(((DefaultHttp3SettingsFrame) settingsFrame).settings);
        } else {
            for (Map.Entry<Long, Long> entry: settingsFrame) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }
}
