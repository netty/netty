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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.AbstractReferenceCounted;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelException;
import io.netty.handler.codec.http.HttpConstants;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Abstract HttpData implementation
 */
public abstract class AbstractHttpData extends AbstractReferenceCounted implements HttpData {

    protected final String name;
    protected long definedSize;
    protected long size;
    protected Charset charset = HttpConstants.DEFAULT_CHARSET;
    protected boolean completed;

    protected AbstractHttpData(String name, Charset charset, long size) {
        this(name, charset, size, false);
    }

    /**
     * Convenient method to check if an attribute name respects the W3C rules
     * (character are ASCII (less than 127) and no characters from "=,; \t\r\n\v\f:")
     * @param name the attribute name to check
     * @return True if the name is conform with the W3C rules
     */
    public static boolean isValidHtml4Name(String name) {
        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c > 127) {
                return false;
            }

            // Check prohibited characters.
            switch (c) {
            case '=':
            case ',':
            case ';':
            case ' ':
            case '\t':
            case '\r':
            case '\n':
            case '\f':
            case 0x0b: // Vertical tab
                 return false;
            }
        }
        return true;
    }

    protected AbstractHttpData(String name, Charset charset, long size, boolean checkBadName) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }
        if (checkBadName && ! isValidHtml4Name(name)) {
            throw new IllegalArgumentException(
                    "name contains non-ascii character or one of the following prohibited characters: " +
                            "=,; \\t\\r\\n\\v\\f: " + name);
        }
        this.name = name;
        if (charset != null) {
            setCharset(charset);
        }
        definedSize = size;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public void setCharset(Charset charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.charset = charset;
    }

    @Override
    public long length() {
        return size;
    }

    @Override
    public ByteBuf data() {
        try {
            return getByteBuf();
        } catch (IOException e) {
            throw new ChannelException(e);
        }
    }

    @Override
    protected void deallocate() {
        delete();
    }

    @Override
    public HttpData retain() {
        super.retain();
        return this;
    }

    @Override
    public HttpData retain(int increment) {
        super.retain(increment);
        return this;
    }
}
