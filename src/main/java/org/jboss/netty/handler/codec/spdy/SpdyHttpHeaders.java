/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.spdy;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;

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

        private Names() {
            super();
        }
    }

    private SpdyHttpHeaders() {
    }

    /**
     * Removes the {@code "X-SPDY-Stream-ID"} header.
     */
    public static void removeStreamID(HttpMessage message) {
        message.removeHeader(Names.STREAM_ID);
    }

    /**
     * Returns the value of the {@code "X-SPDY-Stream-ID"} header.
     */
    public static int getStreamID(HttpMessage message) {
        return HttpHeaders.getIntHeader(message, Names.STREAM_ID);
    }

    /**
     * Sets the {@code "X-SPDY-Stream-ID"} header.
     */
    public static void setStreamID(HttpMessage message, int streamID) {
        HttpHeaders.setIntHeader(message, Names.STREAM_ID, streamID);
    }

    /**
     * Removes the {@code "X-SPDY-Associated-To-Stream-ID"} header.
     */
    public static void removeAssociatedToStreamID(HttpMessage message) {
        message.removeHeader(Names.ASSOCIATED_TO_STREAM_ID);
    }

    /**
     * Returns the value of the {@code "X-SPDY-Associated-To-Stream-ID"} header.
     *
     * @return the header value or {@code 0} if there is no such header or
     *         if the header value is not a number
     */
    public static int getAssociatedToStreamID(HttpMessage message) {
        return HttpHeaders.getIntHeader(message, Names.ASSOCIATED_TO_STREAM_ID, 0);
    }

    /**
     * Sets the {@code "X-SPDY-Associated-To-Stream-ID"} header.
     */
    public static void setAssociatedToStreamID(HttpMessage message, int associatedToStreamID) {
        HttpHeaders.setIntHeader(message, Names.ASSOCIATED_TO_STREAM_ID, associatedToStreamID);
    }

    /**
     * Removes the {@code "X-SPDY-Priority"} header.
     */
    public static void removePriority(HttpMessage message) {
        message.removeHeader(Names.PRIORITY);
    }

    /**
     * Returns the value of the {@code "X-SPDY-Priority"} header.
     *
     * @return the header value or {@code 0} if there is no such header or
     *         if the header value is not a number
     */
    public static byte getPriority(HttpMessage message) {
        return (byte) HttpHeaders.getIntHeader(message, Names.PRIORITY, 0);
    }

    /**
     * Sets the {@code "X-SPDY-Priority"} header.
     */
    public static void setPriority(HttpMessage message, byte priority) {
        HttpHeaders.setIntHeader(message, Names.PRIORITY, priority);
    }

    /**
     * Removes the {@code "X-SPDY-URL"} header.
     */
    public static void removeUrl(HttpMessage message) {
        message.removeHeader(Names.URL);
    }

    /**
     * Returns the value of the {@code "X-SPDY-URL"} header.
     */
    public static String getUrl(HttpMessage message) {
        return message.getHeader(Names.URL);
    }

    /**
     * Sets the {@code "X-SPDY-URL"} header.
     */
    public static void setUrl(HttpMessage message, String url) {
        message.setHeader(Names.URL, url);
    }
}
