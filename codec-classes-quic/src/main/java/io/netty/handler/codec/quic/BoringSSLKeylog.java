/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.codec.quic;

import javax.net.ssl.SSLEngine;


/**
 * Allow to log keys, logging keys are following
 * <a href="https://developer.mozilla.org/en-US/docs/Mozilla/Projects/NSS/Key_Log_Format">
 *     NSS Key Log Format</a>. This is intended for debugging use with tools like Wireshark.
 */
public interface BoringSSLKeylog {

    /**
     * Called when a key should be logged.
     *
     * @param engine    the engine.
     * @param key       the key.
     */
    void logKey(SSLEngine engine, String key);
}
