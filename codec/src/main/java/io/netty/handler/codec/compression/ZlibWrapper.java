/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.compression;

/**
 * The container file formats that wrap the stream compressed by the DEFLATE
 * algorithm.
 */
public enum ZlibWrapper {
    /**
     * The ZLIB wrapper as specified in <a href="http://tools.ietf.org/html/rfc1950">RFC 1950</a>.
     */
    ZLIB,
    /**
     * The GZIP wrapper as specified in <a href="http://tools.ietf.org/html/rfc1952">RFC 1952</a>.
     */
    GZIP,
    /**
     * Raw DEFLATE stream only (no header and no footer).
     */
    NONE,
    /**
     * Try {@link #ZLIB} first and then {@link #NONE} if the first attempt fails.
     * Please note that you can specify this wrapper type only when decompressing.
     */
    ZLIB_OR_NONE
}
