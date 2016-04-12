/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.util.internal.UnstableApi;

/**
 * Registry of all standard frame types defined by the HTTP/2 specification.
 */
@UnstableApi
public final class Http2FrameTypes {
    public static final byte DATA = 0x0;
    public static final byte HEADERS = 0x1;
    public static final byte PRIORITY = 0x2;
    public static final byte RST_STREAM = 0x3;
    public static final byte SETTINGS = 0x4;
    public static final byte PUSH_PROMISE = 0x5;
    public static final byte PING = 0x6;
    public static final byte GO_AWAY = 0x7;
    public static final byte WINDOW_UPDATE = 0x8;
    public static final byte CONTINUATION = 0x9;

    private Http2FrameTypes() {
    }
}
