/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.codehaus.jackson.io.JsonStringEncoder;

/**
 * A MessageFrame carries application data, and consists of any array of JSON encoded messages.
 */
public class MessageFrame implements Frame {

    private final List<String> messages;
    private final ByteBuf content;

    public MessageFrame(final String message) {
        this(new String[] {message});
    }

    public MessageFrame(final String... messages) {
        ArgumentUtil.checkNotNull(messages, "messages");
        this.messages = new ArrayList<String>(Arrays.asList(messages));
        this.content = generateContent(this.messages);
    }

    public List<String> messages() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public ByteBuf content() {
        return content;
    }

    @Override
    public String toString() {
        return "MessageFrame[messages=" + messages + "]";
    }

    private ByteBuf generateContent(final List<String> messages) {
        final int size = messages.size();
        final JsonStringEncoder jsonEndocder = new JsonStringEncoder();
        final ByteBuf content = Unpooled.buffer();
        content.writeByte('a').writeByte('[');
        for (int i = 0; i < size; i++) {
            content.writeByte('"');
            final String escaped = escapeCharacters(jsonEndocder.quoteAsString(messages.get(i)));
            content.writeBytes(Unpooled.copiedBuffer(escaped, CharsetUtil.UTF_8)).writeByte('"');
            if (i < size - 1) {
                content.writeByte(',');
            }
        }
        return content.writeByte(']');
    }

    private static String escapeCharacters(final char[] value) {
        final StringBuilder sb = new StringBuilder(value.length);
        for (int i = 0; i < value.length; i++) {
            final char ch = value[i];
            if ((ch >= '\u0000' && ch <= '\u001F') ||
                    (ch >= '\uD800' && ch <= '\uDFFF') ||
                    (ch >= '\u200C' && ch <= '\u200F') ||
                    (ch >= '\u2028' && ch <= '\u202F') ||
                    (ch >= '\u2060' && ch <= '\u206F') ||
                    (ch >= '\uFFF0' && ch <= '\uFFFF')) {
                final String ss = Integer.toHexString(ch);
                sb.append('\\');
                sb.append('u');
                for (int k = 0; k < 4 - ss.length(); k++) {
                    sb.append('0');
                }
                sb.append(ss.toLowerCase());
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

}
