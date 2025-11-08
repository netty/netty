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

import io.netty.util.internal.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Default implementation of {@link Http3SettingsFrame}.
 *
 * <p>
 * Internally backed by {@link Http3Settings}.
 * The legacy {@link Iterable} and {@link #get(long)} / {@link #put(long, Long)} methods
 * are preserved for backward compatibility.
 * </p>
 */
public final class DefaultHttp3SettingsFrame implements Http3SettingsFrame {

    private final Http3Settings settings;

    public DefaultHttp3SettingsFrame() {
        this.settings = new Http3Settings(4);
    }

    @Override
    public Http3Settings settings() {
        return this.settings;
    }

    @Override
    @Nullable
    public Long get(long key) {
        return this.settings.get(key);
    }

    @Override
    @Nullable
    public Long put(long key, Long value) {
        if (Http3CodecUtils.isReservedHttp2Setting(key)) {
            throw new IllegalArgumentException("Setting is reserved for HTTP/2: " + key);
        }
        return this.settings.put(key, value);
    }


    @Override
    public Iterator<Map.Entry<Long, Long>> iterator() {
       return this.settings.iterator();
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
            copy.settings.copyFrom(((DefaultHttp3SettingsFrame) settingsFrame).settings);
        } else {
            for (Map.Entry<Long, Long> entry : settingsFrame) { 
                copy.settings.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }
}
