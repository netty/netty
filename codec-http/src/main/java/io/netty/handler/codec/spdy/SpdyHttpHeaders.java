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
package io.netty.handler.codec.spdy;

import io.netty.handler.codec.http.HttpHeader;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * Provides the constants for the header names and the utility methods
 * used by the {@link SpdyHttpDecoder} and {@link SpdyHttpEncoder}.
 * @apiviz.stereotype static
 */
public final class SpdyHttpHeaders {

    /**
     * SPDY HTTP header names
     * @apiviz.stereotype static
     */
    public static final class Names {
        /**
         * {@code "X-SPDY-Stream-ID"}
         */
        public static final String STREAM_ID = "X-SPDY-Stream-ID";
        /**
         * {@code "X-SPDY-Associated-To-Stream-ID"}
         */
        public static final String ASSOCIATED_TO_STREAM_ID = "X-SPDY-Associated-To-Stream-ID";
        /**
         * {@code "X-SPDY-Priority"}
         */
        public static final String PRIORITY = "X-SPDY-Priority";
        /**
         * {@code "X-SPDY-URL"}
         */
        public static final String URL = "X-SPDY-URL";
        /**
         * {@code "X-SPDY-Scheme"}
         */
        public static final String SCHEME = "X-SPDY-Scheme";

        private Names() { }
    }

    private SpdyHttpHeaders() {
    }

    /**
     * Removes the {@code "X-SPDY-Stream-ID"} header.
     */
    public static void removeStreamId(HttpHeader message) {
        message.removeHeader(Names.STREAM_ID);
    }

    /**
     * Returns the value of the {@code "X-SPDY-Stream-ID"} header.
     */
    public static int getStreamId(HttpHeader message) {
        return HttpHeaders.getIntHeader(message, Names.STREAM_ID);
    }

    /**
     * Sets the {@code "X-SPDY-Stream-ID"} header.
     */
    public static void setStreamId(HttpHeader message, int streamId) {
        HttpHeaders.setIntHeader(message, Names.STREAM_ID, streamId);
    }

    /**
     * Removes the {@code "X-SPDY-Associated-To-Stream-ID"} header.
     */
    public static void removeAssociatedToStreamId(HttpHeader message) {
        message.removeHeader(Names.ASSOCIATED_TO_STREAM_ID);
    }

    /**
     * Returns the value of the {@code "X-SPDY-Associated-To-Stream-ID"} header.
     *
     * @return the header value or {@code 0} if there is no such header or
     *         if the header value is not a number
     */
    public static int getAssociatedToStreamId(HttpHeader message) {
        return HttpHeaders.getIntHeader(message, Names.ASSOCIATED_TO_STREAM_ID, 0);
    }

    /**
     * Sets the {@code "X-SPDY-Associated-To-Stream-ID"} header.
     */
    public static void setAssociatedToStreamId(HttpHeader message, int associatedToStreamId) {
        HttpHeaders.setIntHeader(message, Names.ASSOCIATED_TO_STREAM_ID, associatedToStreamId);
    }

    /**
     * Removes the {@code "X-SPDY-Priority"} header.
     */
    public static void removePriority(HttpHeader message) {
        message.removeHeader(Names.PRIORITY);
    }

    /**
     * Returns the value of the {@code "X-SPDY-Priority"} header.
     *
     * @return the header value or {@code 0} if there is no such header or
     *         if the header value is not a number
     */
    public static byte getPriority(HttpHeader message) {
        return (byte) HttpHeaders.getIntHeader(message, Names.PRIORITY, 0);
    }

    /**
     * Sets the {@code "X-SPDY-Priority"} header.
     */
    public static void setPriority(HttpHeader message, byte priority) {
        HttpHeaders.setIntHeader(message, Names.PRIORITY, priority);
    }

    /**
     * Removes the {@code "X-SPDY-URL"} header.
     */
    public static void removeUrl(HttpHeader message) {
        message.removeHeader(Names.URL);
    }

    /**
     * Returns the value of the {@code "X-SPDY-URL"} header.
     */
    public static String getUrl(HttpHeader message) {
        return message.getHeader(Names.URL);
    }

    /**
     * Sets the {@code "X-SPDY-URL"} header.
     */
    public static void setUrl(HttpHeader message, String url) {
        message.setHeader(Names.URL, url);
    }

    /**
     * Removes the {@code "X-SPDY-Scheme"} header.
     */
    public static void removeScheme(HttpHeader message) {
        message.removeHeader(Names.SCHEME);
    }

    /**
     * Returns the value of the {@code "X-SPDY-Scheme"} header.
     */
    public static String getScheme(HttpHeader message) {
        return message.getHeader(Names.SCHEME);
    }

    /**
     * Sets the {@code "X-SPDY-Scheme"} header.
     */
    public static void setScheme(HttpHeader message, String scheme) {
        message.setHeader(Names.SCHEME, scheme);
    }
}
