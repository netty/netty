/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelPipeline;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An HTTP chunk which is used for HTTP chunked transfer-encoding.
 * {@link HttpMessageDecoder} generates {@link HttpChunk} after
 * {@link HttpMessage} when the content is large or the encoding of the content
 * is 'chunked.  If you prefer not to receive {@link HttpChunk} in your handler,
 * please {@link HttpChunkAggregator} after {@link HttpMessageDecoder} in the
 * {@link ChannelPipeline}.
 * @apiviz.landmark
 */
public interface HttpChunk {

    /**
     * The 'end of content' marker in chunked encoding.
     */
    HttpChunkTrailer LAST_CHUNK = new HttpChunkTrailer() {
        @Override
        public ChannelBuffer getContent() {
            return ChannelBuffers.EMPTY_BUFFER;
        }

        @Override
        public void setContent(ChannelBuffer content) {
            throw new IllegalStateException("read-only");
        }

        @Override
        public boolean isLast() {
            return true;
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
    };

    /**
     * Returns {@code true} if and only if this chunk is the 'end of content'
     * marker.
     */
    boolean isLast();

    /**
     * Returns the content of this chunk.  If this is the 'end of content'
     * marker, {@link ChannelBuffers#EMPTY_BUFFER} will be returned.
     */
    ChannelBuffer getContent();

    /**
     * Sets the content of this chunk.  If an empty buffer is specified,
     * this chunk becomes the 'end of content' marker.
     */
    void setContent(ChannelBuffer content);
}
