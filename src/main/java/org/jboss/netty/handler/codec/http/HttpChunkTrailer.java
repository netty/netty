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
package org.jboss.netty.handler.codec.http;

import java.util.List;
import java.util.Set;

/**
 * The last {@link HttpChunk} which has trailing headers.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public interface HttpChunkTrailer extends HttpChunk {

    /**
     * Always returns {@code true}.
     */
    boolean isLast();

    /**
     * Returns the trailing header value with the specified header name.
     * If there are more than one trailing header value for the specified
     * header name, the first value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     *
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
    void addHeader(String name, String value);

    /**
     * Sets a new trailing header with the specified name and value.
     * If there is an existing trailing header with the same name, the existing
     * one is removed.
     */
    void setHeader(String name, String value);

    /**
     * Sets a new trailing header with the specified name and values.
     * If there is an existing trailing header with the same name, the existing
     * one is removed.
     */
    void setHeader(String name, Iterable<String> values);

    /**
     * Removes the trailing header with the specified name.
     */
    void removeHeader(String name);

    /**
     * Removes all trailing headers from this trailer.
     */
    void clearHeaders();
}
