/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.http2;

/**
 * Signifies that the <a href="https://tools.ietf.org/html/rfc7540#section-3.5">connection preface</a> and
 * the initial SETTINGS frame have been sent. The client sends the preface, and the server receives the preface.
 * The client shouldn't write any data until this event has been processed.
 */
public final class Http2ConnectionPrefaceAndSettingsFrameWrittenEvent {
    static final Http2ConnectionPrefaceAndSettingsFrameWrittenEvent INSTANCE =
            new Http2ConnectionPrefaceAndSettingsFrameWrittenEvent();

    private Http2ConnectionPrefaceAndSettingsFrameWrittenEvent() {
    }
}
