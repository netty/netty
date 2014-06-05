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

package io.netty.handler.codec.http;

import io.netty.handler.codec.EmptyTextHeaders;
import io.netty.handler.codec.TextHeaderProcessor;
import io.netty.handler.codec.TextHeaders;

public class EmptyHttpHeaders extends EmptyTextHeaders implements HttpHeaders {

    public static final EmptyHttpHeaders INSTANCE = new EmptyHttpHeaders();

    protected EmptyHttpHeaders() { }

    @Override
    public HttpHeaders add(CharSequence name, Object value) {
        super.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Object... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(TextHeaders headers) {
        super.add(headers);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Object value) {
        super.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Object... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(TextHeaders headers) {
        super.set(headers);
        return this;
    }

    @Override
    public HttpHeaders clear() {
        super.clear();
        return this;
    }

    @Override
    public HttpHeaders forEachEntry(TextHeaderProcessor processor) {
        super.forEachEntry(processor);
        return this;
    }
}
