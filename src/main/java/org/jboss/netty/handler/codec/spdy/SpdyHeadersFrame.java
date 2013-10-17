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
package org.jboss.netty.handler.codec.spdy;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A SPDY Protocol HEADERS Frame
 */
public interface SpdyHeadersFrame extends SpdyStreamFrame {

    /**
     * Returns {@code true} if this header block is invalid.
     * A RST_STREAM frame with code PROTOCOL_ERROR should be sent.
     */
    boolean isInvalid();

    /**
     * Marks this header block as invalid.
     */
    void setInvalid();

    /**
     * Returns {@code true} if this header block has been truncated due to
     * length restrictions.
     */
    boolean isTruncated();

    /**
     * Mark this header block as truncated.
     */
    void setTruncated();

    /**
     * Returns the {@link SpdyHeaders}.
     */
    SpdyHeaders headers();

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    String getHeader(String name);

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    List<String> getHeaders(String name);

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    List<Map.Entry<String, String>> getHeaders();

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    boolean containsHeader(String name);

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    Set<String> getHeaderNames();

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    void addHeader(String name, Object value);

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    void setHeader(String name, Object value);

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    void setHeader(String name, Iterable<?> values);

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    void removeHeader(String name);

    /**
     * @deprecated Use {@link SpdyHeaders#headers()} instead.
     */
    @Deprecated
    void clearHeaders();
}
