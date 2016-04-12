/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.memcache.binary;

import io.netty.util.internal.UnstableApi;

/**
 * Contains all possible status values a {@link BinaryMemcacheResponse} can return.
 */
@UnstableApi
public final class BinaryMemcacheResponseStatus {

    private BinaryMemcacheResponseStatus() {
        // disallow construction
    }

    public static final short SUCCESS = 0x00;
    public static final short KEY_ENOENT = 0x01;
    public static final short KEY_EEXISTS = 0x02;
    public static final short E2BIG = 0x03;
    public static final short EINVA = 0x04;
    public static final short NOT_STORED = 0x05;
    public static final short DELTA_BADVAL = 0x06;
    public static final short AUTH_ERROR = 0x20;
    public static final short AUTH_CONTINUE = 0x21;
    public static final short UNKNOWN_COMMAND = 0x81;
    public static final short ENOMEM = 0x82;
}
