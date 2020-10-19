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

import io.netty.buffer.ByteBuf;

/**
 * Combines {@link StompHeadersSubframe} and {@link LastStompContentSubframe} into one
 * frame. So it represent a <i>complete</i> STOMP frame.
 */
public interface StompFrame extends StompHeadersSubframe, LastStompContentSubframe {
    @Override
    StompFrame copy();

    @Override
    StompFrame duplicate();

    @Override
    StompFrame retainedDuplicate();

    @Override
    StompFrame replace(ByteBuf content);

    @Override
    StompFrame retain();

    @Override
    StompFrame retain(int increment);

    @Override
    StompFrame touch();

    @Override
    StompFrame touch(Object hint);
}
