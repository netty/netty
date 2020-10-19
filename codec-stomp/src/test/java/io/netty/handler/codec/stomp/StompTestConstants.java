/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

public final class StompTestConstants {
    public static final String CONNECT_FRAME =
        "CONNECT\n" +
            "host:stomp.github.org\n" +
            "accept-version:1.1,1.2\n" +
            '\n' +
            '\0';
    public static final String CONNECTED_FRAME =
        "CONNECTED\n" +
            "version:1.2\n" +
            '\n' +
            "\0\n";
    public static final String SEND_FRAME_1 =
        "SEND\n" +
            "destination:/queue/a\n" +
            "content-type:text/plain\n" +
            '\n' +
            "hello, queue a!" +
            "\0\n";
    public static final String SEND_FRAME_2 =
        "SEND\n" +
            "destination:/queue/a\n" +
            "content-type:text/plain\n" +
            "content-length:17\n" +
            '\n' +
            "hello, queue a!!!" +
            "\0\n";
    public static final String[] SEND_FRAMES_3 = {
            "SEND\n" +
                    "destination:/queue/a\n" +
                    "content-type:text/plain\n" +
                    '\n' +
                    "first part of body\n",
            "second part of body\0"
    };

    public static final String SEND_FRAME_4 = "SEND\n" +
            "destination:/queue/a\n" +
            "content-type:text/plain\n" +
            '\n' +
            "body\0";

    public static final String FRAME_WITH_INVALID_HEADER = "SEND\n" +
            "destination:/some-destination\n" +
            "content-type:text/plain\n" +
            "current-time:2000-01-01T00:00:00\n" +
            '\n' +
            "some body\0";

    public static final String FRAME_WITH_EMPTY_HEADER_NAME = "SEND\n" +
            "destination:/some-destination\n" +
            "content-type:text/plain\n" +
            ":header-value\n" +
            '\n' +
            "some body\0";

    public static final String SEND_FRAME_UTF8 = "SEND\n" +
            "destination:/queue/№11±♛нетти♕\n" +
            "content-type:text/plain\n" +
            '\n' +
            "body\0";

    private StompTestConstants() { }
}
