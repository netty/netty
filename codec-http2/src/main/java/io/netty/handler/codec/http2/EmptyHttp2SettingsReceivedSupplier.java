/*
 * Copyright 2019 The Netty Project
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
package io.netty.handler.codec.http2;

final class EmptyHttp2SettingsReceivedSupplier implements Http2SettingReceivedSupplier {
    static final Http2SettingReceivedSupplier INSTANCE = new EmptyHttp2SettingsReceivedSupplier();

    private EmptyHttp2SettingsReceivedSupplier() {
        // singleton
    }

    @Override
    public Http2Settings pollReceivedSettings() {
        return null;
    }
}
