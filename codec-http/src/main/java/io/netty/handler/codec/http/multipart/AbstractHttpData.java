/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelException;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.internal.ObjectUtil;

import java.io.IOException;
import java.nio.charset.Charset;

import static io.netty.util.internal.ObjectUtil.checkNonEmpty;

/**
 * Abstract HttpData implementation
 */
public abstract class AbstractHttpData extends AbstractReferenceCounted implements HttpData {

    private final String name;
    protected long definedSize;
    protected long size;
    private Charset charset = HttpConstants.DEFAULT_CHARSET;
    private boolean completed;
    private long maxSize = DefaultHttpDataFactory.MAXSIZE;

    protected AbstractHttpData(String name, Charset charset, long size) {
        ObjectUtil.checkNotNull(name, "name");

        this.name = checkNonEmpty(cleanName(name), "name");
        if (charset != null) {
            setCharset(charset);
        }
        definedSize = size;
    }

    //Replaces \r and \t with a space
    //Removes leading and trailing whitespace and newlines
    private static String cleanName(String name) {
        int len = name.length();
        StringBuilder sb = null;

        int start = 0;
        int end = len;

        // Trim leading whitespace
        while (start < end && Character.isWhitespace(name.charAt(start))) {
            start++;
        }

        // Trim trailing whitespace
        while (end > start && Character.isWhitespace(name.charAt(end - 1))) {
            end--;
        }

        for (int i = start; i < end; i++) {
            char c = name.charAt(i);

            if (c == '\n') {
                // Skip newline entirely
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(name, start, i);
                }
                continue;
            }

            if (c == '\r' || c == '\t') {
                if (sb == null) {
                    sb = new StringBuilder(len);
                    sb.append(name, start, i);
                }
                sb.append(' ');
            } else if (sb != null) {
                sb.append(c);
            }
        }

        // If no replacements were needed, return the trimmed slice
        return sb == null ? name.substring(start, end) : sb.toString();
    }

    @Override
    public long getMaxSize() {
        return maxSize;
    }

    @Override
    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void checkSize(long newSize) throws IOException {
        if (maxSize >= 0 && newSize > maxSize) {
            throw new IOException("Size exceed allowed maximum capacity");
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isCompleted() {
        return completed;
    }

    protected void setCompleted() {
        setCompleted(true);
    }

    protected void setCompleted(boolean completed) {
        this.completed = completed;
    }

    @Override
    public Charset getCharset() {
        return charset;
    }

    @Override
    public void setCharset(Charset charset) {
        this.charset = ObjectUtil.checkNotNull(charset, "charset");
    }

    @Override
    public long length() {
        return size;
    }

    @Override
    public long definedLength() {
        return definedSize;
    }

    @Override
    public ByteBuf content() {
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

    @Override
    public abstract HttpData touch();

    @Override
    public abstract HttpData touch(Object hint);
}
