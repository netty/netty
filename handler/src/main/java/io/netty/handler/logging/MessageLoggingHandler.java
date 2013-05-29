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
    public void write(ChannelHandlerContext ctx, Object[] msgs, int index, int length, ChannelPromise promise) throws Exception {
        log("WRITE", msgs, index, length);
        ctx.write(msgs, index, length, promise);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Object[] msgs, int index, int length) throws Exception {
        log("RECEIVED", msgs, index, length);
        ctx.fireMessageReceived(msgs, index, length);
    }

    private void log(String message, Object[] msgs, int index, int length) {
        if (logger.isEnabled(internalLevel)) {
            logger.log(internalLevel, formatBuffer(message, msgs, index, length));
        }
    }

    protected String formatBuffer(String message, Object[] msgs, int index, int length) {
        return message + '(' + length + "): " + contentToString(msgs, index, length);
    }

    private static String contentToString(Object[] msgs, int index, int length) {
        if (length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = index; i < length; i++) {
            Object msg = msgs[i];
            sb.append(msg);

            if (i + 1 < length) {
                sb.append(", ");
            }
        }
        return sb.append(']').toString();
    }
}
