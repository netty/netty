/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link Http2SettingsFrame} implementation.
 */
public class DefaultHttp2SettingsFrame implements Http2SettingsFrame {

    private final Http2Settings settings;

    public DefaultHttp2SettingsFrame(Http2Settings settings) {
        this.settings = ObjectUtil.checkNotNull(settings, "settings");
    }

    @Override
    public Http2Settings settings() {
        return settings;
    }

    @Override
    public String name() {
        return "SETTINGS";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Http2SettingsFrame)) {
            return false;
        }
        Http2SettingsFrame other = (Http2SettingsFrame) o;
        return settings.equals(other.settings());
    }

    @Override
    public int hashCode() {
        return settings.hashCode();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(settings=" + settings + ')';
    }
}
