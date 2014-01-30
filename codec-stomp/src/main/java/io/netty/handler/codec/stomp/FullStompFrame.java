/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

/**
 * Combines {@link StompFrame} and {@link LastStompContent} into one
 * frame. So it represent a <i>complete</i> STOMP frame.
 */
public interface FullStompFrame extends StompFrame, LastStompContent {
    @Override
    FullStompFrame copy();

    @Override
    FullStompFrame duplicate();

    @Override
    FullStompFrame retain();

    @Override
    FullStompFrame retain(int increment);

    @Override
    FullStompFrame touch();

    @Override
    FullStompFrame touch(Object hint);

}
