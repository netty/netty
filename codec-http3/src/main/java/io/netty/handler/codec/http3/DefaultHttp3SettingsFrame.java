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
package io.netty.handler.codec.http3;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

/**
 * Default implementation of {@link Http3SettingsFrame}.
 *
 */
public final class DefaultHttp3SettingsFrame implements Http3SettingsFrame {

    private final Http3Settings settings;

    public DefaultHttp3SettingsFrame(Http3Settings settings) {
        this.settings = ObjectUtil.checkNotNull(settings, "settings");
    }

    public DefaultHttp3SettingsFrame(){
        this.settings = new Http3Settings();
    }

    @Override
    public Http3Settings settings() {
        return settings;
    }

    /**
     * Get a setting by its key.
     *
     * @param key the HTTP/3 setting key
     * @return the value, or {@code null} if not set
     * @deprecated  use {@link #settings()}  and manipulate the {@link Http3Settings} directly
     */
    @Override
    @Deprecated
    @Nullable
    public Long get(long key) {
        // Legacy behavior: direct map access.
        return settings.get(key);
    }

    /**
     * Set a setting value by key.
     *
     * @param key the HTTP/3 setting key
     * @param value the value to set
     * @return the previous value, or {@code null} if none
     * @throws IllegalArgumentException if the key is reserved for HTTP/2
     * @deprecated  use {@link #settings()}  and manipulate the {@link Http3Settings} directly
     */
    @Override
    @Deprecated
    @Nullable
    public Long put(long key, Long value) {
        return settings.put(key, value);
    }



    @Override
    public Iterator<Map.Entry<Long, Long>> iterator() {
       return settings.iterator();
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
