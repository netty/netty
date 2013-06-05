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
package io.netty.handler.logging;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MessageList;

public class MessageLoggingHandler extends LoggingHandler {

    public MessageLoggingHandler() { }

    public MessageLoggingHandler(Class<?> clazz, LogLevel level) {
        super(clazz, level);
    }

    public MessageLoggingHandler(Class<?> clazz) {
        super(clazz);
    }

    public MessageLoggingHandler(LogLevel level) {
        super(level);
    }

    public MessageLoggingHandler(String name, LogLevel level) {
        super(name, level);
    }

    public MessageLoggingHandler(String name) {
        super(name);
    }

    @Override
    public void write(ChannelHandlerContext ctx, MessageList<Object> msgs,  ChannelPromise promise) throws Exception {
        log("WRITE", msgs);
        ctx.write(msgs);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageList<Object> msgs) throws Exception {
        log("RECEIVED", msgs);
        ctx.fireMessageReceived(msgs);
    }

    private void log(String message, MessageList<Object> msgs) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, formatBuffer(message, msgs));
        }
    }

    protected String formatBuffer(String message, MessageList<Object> msgs) {
        return message + '(' + msgs.size() + "): " + contentToString(msgs);
    }

    private static String contentToString(MessageList<Object> msgs) {
        if (msgs.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        int size = msgs.size();
        for (int i = 0; i < size; i++) {
            Object msg = msgs.get(i);
            sb.append(msg);

            if (i + 1 < size) {
                sb.append(", ");
            }
        }
        return sb.append(']').toString();
    }
}
