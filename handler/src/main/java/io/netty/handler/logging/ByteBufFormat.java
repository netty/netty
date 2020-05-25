/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.logging;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;

/**
 * Used to control the format and verbosity of logging for {@link ByteBuf}s and {@link ByteBufHolder}s.
 *
 * @see LoggingHandler
 */
public enum ByteBufFormat {
    /**
     * {@link ByteBuf}s will be logged in a simple format, with no hex dump included.
     */
    SIMPLE,
    /**
     * {@link ByteBuf}s will be logged using {@link ByteBufUtil#appendPrettyHexDump(StringBuilder, ByteBuf)}.
     */
    HEX_DUMP
}
