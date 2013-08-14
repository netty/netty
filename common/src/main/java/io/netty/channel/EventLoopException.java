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
package io.netty.channel;

/**
 * Special {@link ChannelException} which will be thrown by {@link EventLoop} and {@link EventLoopGroup}
 * implementations when an error occurs.
 */
public class EventLoopException extends ChannelException {

    private static final long serialVersionUID = -8969100344583703616L;

    public EventLoopException() {
    }

    public EventLoopException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventLoopException(String message) {
        super(message);
    }

    public EventLoopException(Throwable cause) {
        super(cause);
    }

}
