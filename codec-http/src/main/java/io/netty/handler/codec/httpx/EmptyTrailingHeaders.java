/*
 * Copyright 2013 The Netty Project
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

package io.netty.handler.codec.httpx;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

final class EmptyTrailingHeaders extends AbstractHttpHeaders {

    static final HttpHeaders INSTANCE = new EmptyTrailingHeaders();

    private EmptyTrailingHeaders() {
        super(HttpMessageType.TRAILER);
    }

    private static HttpHeaders fail() {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    protected void add0(String name, String value) {
        fail();
    }

    @Override
    protected void set0(String name, String value) {
        fail();
    }

    @Override
    protected void remove0(String name, String value) {
        fail();
    }

    @Override
    public HttpVersion getVersion() {
        return null;
    }

    @Override
    public HttpHeaders setVersion(HttpVersion version) {
        return fail();
    }

    @Override
    public HttpMethod getMethod() {
        return null;
    }

    @Override
    public HttpHeaders setMethod(HttpMethod method) {
        return fail();
    }

    @Override
    public HttpResponseStatus getStatus() {
        return null;
    }

    @Override
    public HttpHeaders setStatus(HttpResponseStatus status) {
        return fail();
    }

    @Override
    public String getUri() {
        return null;
    }

    @Override
    public HttpHeaders setUri(String uri) {
        return fail();
    }

    @Override
    public String get(String name) {
        return null;
    }

    @Override
    public List<String> getAll(String name) {
        return Collections.emptyList();
    }

    @Override
    public List<Entry<String, String>> entries() {
        return Collections.emptyList();
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Set<String> names() {
        return Collections.emptySet();
    }

    @Override
    public HttpHeaders remove(String name) {
        return fail();
    }

    @Override
    public HttpHeaders clear() {
        return fail();
    }

    @Override
    public HttpHeaders set(HttpHeaders headers) {
        return fail();
    }

    @Override
    public HttpHeaders add(HttpHeaders headers) {
        return fail();
    }

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        return fail();
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        return fail();
    }
}
