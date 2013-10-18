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
package org.jboss.netty.handler.codec.http;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The last {@link HttpChunk} which has trailing headers.
 */
public interface HttpChunkTrailer extends HttpChunk {

    /**
     * Always returns {@code true}.
     */
    boolean isLast();

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    String getHeader(String name);

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    List<String> getHeaders(String name);

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    List<Map.Entry<String, String>> getHeaders();

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    boolean containsHeader(String name);

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    Set<String> getHeaderNames();

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    void addHeader(String name, Object value);

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    void setHeader(String name, Object value);

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    void setHeader(String name, Iterable<?> values);

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    void removeHeader(String name);

    /**
     * @deprecated Use {@link HttpChunkTrailer#trailingHeaders()} instead.
     */
    @Deprecated
    void clearHeaders();

    /**
     * Returns the trialing headers of this trailer.
     */
    HttpHeaders trailingHeaders();
}
