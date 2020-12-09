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

import java.util.Map;

/**
 * See <a href="https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.4">SETTINGS</a>.
 */
public interface Http3SettingsFrame extends Http3ControlStreamFrame, Iterable<Map.Entry<Long, Long>> {
    /**
     * Get a setting from the frame.
     *
     * @param key   the key of the setting.
     * @return      the value of the setting or {@code null} if none was found with the given key.
     */
    Long get(long key);

    /**
     * Put a setting in the frame.
     *
     * @param key       the key of the setting
     * @param value     the value of the setting.
     * @return          the previous stored valued for the given key or {@code null} if none was stored before.
     */
    Long put(long key, Long value);
}
