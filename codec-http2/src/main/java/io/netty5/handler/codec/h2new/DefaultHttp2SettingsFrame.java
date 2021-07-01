/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.h2new;

import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.util.internal.StringUtil;

import static io.netty5.util.internal.ObjectUtil.checkNotNullWithIAE;

/**
 * Default implementation of {@link Http2SettingsFrame}.
 */
public final class DefaultHttp2SettingsFrame implements Http2SettingsFrame {
    private final Http2Settings settings;
    private final boolean ack;

    /**
     * Creates a settings ack frame.
     */
    public DefaultHttp2SettingsFrame() {
        ack = true;
        settings = null;
    }

    /**
     * Creates a new instance.
     *
     * @param settings {@link Http2Settings} for this frame.
     */
    public DefaultHttp2SettingsFrame(Http2Settings settings) {
        ack = false;
        this.settings = checkNotNullWithIAE(settings, "settings");
    }

    @Override
    public Type frameType() {
        return Type.Settings;
    }

    @Override
    public int streamId() {
        return 0;
    }

    @Override
    public Http2Settings settings() {
        return settings;
    }

    @Override
    public boolean ack() {
        return ack;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "{settings=" + settings + ", ack=" + ack + '}';
    }
}
