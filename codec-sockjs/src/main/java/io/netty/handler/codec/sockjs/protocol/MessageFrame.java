/*
 * Copyright 2013 The Netty Project
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

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.netty.util.internal.StringUtil;

/**
 * A MessageFrame carries application data, and consists of any array of JSON encoded messages.
 */
public class MessageFrame extends DefaultByteBufHolder implements Frame {

    private final List<String> messages;

    /**
     * Creates a new instance with the specified message as its sole message.
     *
     * @param message the sole message for this MessageFrame.
     */
    public MessageFrame(final String message) {
        this(new String[] {message});
    }

    /**
     * Creates a new instance with the specified messages.
     *
     * @param messages the messages that this message frame will contain.
     */
    public MessageFrame(final String... messages) {
        this(new ArrayList<String>(Arrays.asList(messages)));
    }

    /**
     * Creates a new instance with the specified messages.
     *
     * @param messages the messages that this message frame will contain.
     */
    public MessageFrame(final List<String> messages) {
        super(generateContent(messages));
        this.messages = messages;
    }

    /**
     * Returns this {@link MessageFrame}s messages.
     *
     * @return {@code List} the messages for this {@link MessageFrame}
     */
    public List<String> messages() {
        return Collections.unmodifiableList(messages);
    }

    @Override
    public MessageFrame copy() {
        return new MessageFrame(messages);
    }

    @Override
    public MessageFrame duplicate() {
        return new MessageFrame(messages);
    }

    @Override
    public MessageFrame retain() {
        super.retain();
        return this;
    }

    @Override
    public MessageFrame retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "[messages=" + messages + ']';
    }

    private static ByteBuf generateContent(final List<String> messages) {
        final JsonStringEncoder jsonEndocder = new JsonStringEncoder();
        final ByteBuf content = Unpooled.buffer();
        content.writeByte('a').writeByte('[');
        final int size = messages.size();
        for (int i = 0; i < size; i++) {
            content.writeByte('"');
            final String element = messages.get(i);
            if (element == null) {
                messages.subList(i, size).clear();
                break;
            }
            final String escaped = escapeCharacters(jsonEndocder.quoteAsString(element));
            final ByteBuf escapedBuf = Unpooled.copiedBuffer(escaped, CharsetUtil.UTF_8);
            content.writeBytes(escapedBuf).writeByte('"');
            escapedBuf.release();
            if (i < size - 1) {
                content.writeByte(',');
            }
        }
        return content.writeByte(']');
    }

    private static String escapeCharacters(final char[] value) {
        final StringBuilder sb = new StringBuilder(value.length);
        for (char ch : value) {
            if (from0000To0001F(ch) || fromD800ToDFFF(ch) || from200CTo200F(ch) || from2028To202F(ch) ||
                    from2060To206F(ch) || fromFFF0ToFFFF(ch)) {
                final String ss = Integer.toHexString(ch);
                sb.append("\\u");
                for (int k = 0; k < 4 - ss.length(); k++) {
                    sb.append('0');
                }
                sb.append(ss.toLowerCase(Locale.ENGLISH));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static boolean from0000To0001F(final char ch) {
        return ch >= '\u0000' && ch <= '\u001F';
    }

    private static boolean fromD800ToDFFF(final char ch) {
        return ch >= '\uD800' && ch <= '\uDFFF';
    }

    private static boolean from200CTo200F(final char ch) {
        return ch >= '\u200C' && ch <= '\u200F';
    }

    private static boolean from2028To202F(final char ch) {
        return ch >= '\u2028' && ch <= '\u202F';
    }

    private static boolean from2060To206F(final char ch) {
        return ch >= '\u2060' && ch <= '\u206F';
    }

    private static boolean fromFFF0ToFFFF(final char ch) {
        return ch >= '\uFFF0' && ch <= '\uFFFF';
    }
}
