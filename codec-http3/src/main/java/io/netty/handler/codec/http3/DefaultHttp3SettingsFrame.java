/*
 * Copyright 2020 The Netty Project
 *
 * Licensed under the Apache License, Version 2.0
 * See the License for the specific language governing permissions and limitations under the License.
 */
package io.netty.handler.codec.http3;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.util.Objects;

public final class DefaultHttp3SettingsFrame implements Http3SettingsFrame {

    private final Http3Settings settings;

    public DefaultHttp3SettingsFrame() {
        this(new Http3Settings(4));
    }

    public DefaultHttp3SettingsFrame(Http3Settings settings) {
        this.settings = ObjectUtil.checkNotNull(settings, "settings");
    }

    @Override
    public Http3Settings settings() {
        return settings;
    }

    @Override
    public long type() {
        return Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE;
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
        if (!(o instanceof DefaultHttp3SettingsFrame)) {
            return false;
        }
        DefaultHttp3SettingsFrame that = (DefaultHttp3SettingsFrame) o;
        return Objects.equals(this.settings, that.settings);
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "(settings=" + settings + ')';
    }
}
