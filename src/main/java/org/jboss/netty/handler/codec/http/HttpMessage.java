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
import java.util.Map;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * An HTTP message which provides common properties for {@link HttpRequest} and
 * {@link HttpResponse}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 *
 * @see HttpHeaders
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.handler.codec.http.HttpChunk oneway - - is followed by
 */
public interface HttpMessage {

    /**
     * Returns the header value with the specified header name.  If there are
     * more than one header value for the specified header name, the first
     * value is returned.
     *
     * @return the header value or {@code null} if there is no such header
     *
     */
    String getHeader(String name);

    /**
     * Returns the header values with the specified header name.
     *
     * @return the {@link List} of header values.  An empty list if there is no
     *         such header.
     */
    List<String> getHeaders(String name);

    /**
     * Returns {@code true} if and only if there is a header with the specified
     * header name.
     */
    boolean containsHeader(String name);

    /**
     * Returns the {@link Set} of all header names that this message contains.
     */
    Set<String> getHeaderNames();

    /**
      *
      * Returns the {@link Map} of all header that this message contains, map key being
      * header names, map object being {@link List} of associated header values.
      */
    Map<String, List<String>> getHeaders();

    /**
     * Convenient method to decodes the Cookie HTTP header value into a {@link Set}
     * of {@link Cookie}s.
     *
     * @return the decoded {@link Cookie}s
     */
    Set<Cookie> getCookies();

    /**
      * Convenient method to add a {@link Cookie}
      *
      * @param cookie the cookie to add
      * @param isServer {@code true} if and only if this encoder is supposed to
      *               encode server-side cookies.  {@code false} if and only if
      *               this encoder is supposed to encode client-side cookies.
      */
    void addCookie(Cookie cookie, boolean isServer);

    /**
     * Convenient method to replace all {@link Cookie}s
     *
     * @param cookies
     * @param isServer {@code true} if and only if this encoder is supposed to
     *               encode server-side cookies.  {@code false} if and only if
    *               this encoder is supposed to encode client-side cookies.
     */
    void setCookies(Set<Cookie> cookies, boolean isServer);

    /**
    * Returns the protocol version of this message.
    */
    HttpVersion getProtocolVersion();

    /**
     * Returns the content of this message.  If there is no content, an
     * {@link ChannelBuffers#EMPTY_BUFFER} is returned.
     */
    ChannelBuffer getContent();

    /**
     * Sets the content of this message.  If {@code null} is specified,
     * the content of this message will be set to {@link ChannelBuffers#EMPTY_BUFFER}.
     */
    void setContent(ChannelBuffer content);

    /**
     * Adds a new header with the specified name and value.
     */
    void addHeader(String name, String value);

    /**
     * Sets a new header with the specified name and value.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    void setHeader(String name, String value);

    /**
     * Sets a new header with the specified name and values.  If there is an
     * existing header with the same name, the existing header is removed.
     */
    void setHeader(String name, Iterable<String> values);

    /**
     * Set all headers from this Map. If there is an existing header with the same name,
     * the existing header is removed.
     * @param headers
     */
    void setHeaders(Map<String, List<String>> headers);

    /**
     * Removes the header with the specified name.
     */
    void removeHeader(String name);

    /**
     * Removes all headers from this message.
     */
    void clearHeaders();

    /**
     * Returns the length of the content.  Please note that this value is
     * not retrieved from {@link #getContent()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @return the content length or {@code 0} if this message does not have
     *         the {@code "Content-Length"} header
     */
    long getContentLength();

    /**
     * Returns the length of the content.  Please note that this value is
     * not retrieved from {@link #getContent()} but from the
     * {@code "Content-Length"} header, and thus they are independent from each
     * other.
     *
     * @return the content length or {@code defaultValue} if this message does
     *         not have the {@code "Content-Length"} header
     */
    long getContentLength(long defaultValue);

    /**
     * Returns {@code true} if and only if the {@code "Transfer-Encoding"} of
     * this message is {@code "chunked"}.
     */
    boolean isChunked();

    /**
     * Returns {@code true} if and only if the connection can remain open and
     * thus 'kept alive'.
     */
    boolean isKeepAlive();
}
