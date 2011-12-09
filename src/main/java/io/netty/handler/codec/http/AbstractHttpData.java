/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http;

import java.nio.charset.Charset;

/**
 * Abstract HttpData implementation
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://openr66.free.fr/">Frederic Bregier</a>
 *
 */
public abstract class AbstractHttpData implements HttpData {

    protected final String name;

    protected long definedSize = 0;

    protected long size = 0;

    protected Charset charset = HttpCodecUtil.DEFAULT_CHARSET;

    protected boolean completed = false;

    public AbstractHttpData(String name, Charset charset, long size)
    throws NullPointerException, IllegalArgumentException {
        if (name == null) {
            throw new NullPointerException("name");
        }
        name = name.trim();
        if (name.length() == 0) {
            throw new IllegalArgumentException("empty name");
        }

        for (int i = 0; i < name.length(); i ++) {
            char c = name.charAt(i);
            if (c > 127) {
                throw new IllegalArgumentException(
                        "name contains non-ascii character: " + name);
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
                throw new IllegalArgumentException(
                        "name contains one of the following prohibited characters: " +
                        "=,; \\t\\r\\n\\v\\f: " + name);
            }
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
}
