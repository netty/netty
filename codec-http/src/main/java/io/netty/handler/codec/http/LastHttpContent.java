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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The last {@link HttpContent} which has trailing headers.
 */
public interface LastHttpContent extends HttpContent {

    /**
     * The 'end of content' marker in chunked encoding.
     */
    LastHttpContent EMPTY_LAST_CONTENT = new LastHttpContent() {
        @Override
        public ByteBuf getContent() {
            return Unpooled.EMPTY_BUFFER;
        }

        @Override
        public void setContent(ByteBuf content) {
            throw new IllegalStateException("read-only");
        }

        @Override
        public void addHeader(String name, Object value) {
            throw new IllegalStateException("read-only");
        }

        @Override
        public void clearHeaders() {
            // NOOP
        }

        @Override
        public boolean containsHeader(String name) {
            return false;
        }

        @Override
        public String getHeader(String name) {
            return null;
        }

        @Override
        public Set<String> getHeaderNames() {
            return Collections.emptySet();
        }

        @Override
        public List<String> getHeaders(String name) {
            return Collections.emptyList();
        }

        @Override
        public List<Map.Entry<String, String>> getHeaders() {
            return Collections.emptyList();
        }

        @Override
        public void removeHeader(String name) {
            // NOOP
        }

        @Override
        public void setHeader(String name, Object value) {
            throw new IllegalStateException("read-only");
        }

        @Override
        public void setHeader(String name, Iterable<?> values) {
            throw new IllegalStateException("read-only");
        }

        @Override
        public DecoderResult getDecoderResult() {
            return DecoderResult.SUCCESS;
        }

        @Override
        public void setDecoderResult(DecoderResult result) {
            throw new IllegalStateException("read-only");
        }
    };

    /**
     * Returns the trailing header value with the specified header name.
     * If there are more than one trailing header value for the specified
     * header name, the first value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     */
    String getHeader(String name);

    /**
     * Returns the trailing header values with the specified header name.
     *
     * @return the {@link List} of header values.  An empty list if there is no
     *         such header.
     */
    List<String> getHeaders(String name);

    /**
     * Returns the all header names and values that this trailer contains.
     *
     * @return the {@link List} of the header name-value pairs.  An empty list
     *         if there is no header in this trailer.
     */
    List<Map.Entry<String, String>> getHeaders();

    /**
     * Returns {@code true} if and only if there is a trailing header with
     * the specified header name.
     */
    boolean containsHeader(String name);

    /**
     * Returns the {@link Set} of all trailing header names that this trailer
     * contains.
     */
    Set<String> getHeaderNames();

    /**
     * Adds a new trailing header with the specified name and value.
     */
    void addHeader(String name, Object value);

    /**
     * Sets a new trailing header with the specified name and value.
     * If there is an existing trailing header with the same name, the existing
     * one is removed.
     */
    void setHeader(String name, Object value);

    /**
     * Sets a new trailing header with the specified name and values.
     * If there is an existing trailing header with the same name, the existing
     * one is removed.
     */
    void setHeader(String name, Iterable<?> values);

    /**
     * Removes the trailing header with the specified name.
     */
    void removeHeader(String name);

    /**
     * Removes all trailing headers from this trailer.
     */
    void clearHeaders();
}
