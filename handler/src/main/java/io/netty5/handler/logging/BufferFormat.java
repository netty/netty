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
package io.netty5.handler.logging;

/**
 * Used to control the format and verbosity of logging for buffers and buffer-like objects.
 *
 * @see LoggingHandler
 */
public enum BufferFormat {
    /**
     * Buffers will be logged in a simple format, with no hex dump included.
     */
    SIMPLE,
    /**
     * Buffers will be logged as hex-dumps.
     */
    HEX_DUMP
}
