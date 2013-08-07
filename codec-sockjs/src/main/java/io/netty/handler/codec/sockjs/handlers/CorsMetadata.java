/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.handlers;

/**
 * Stores Cross Origin Resource Sharing (CORS) information.
 *
 * This class can be used to store incoming CORS information like 'origin' and 'headers
 * which can later then be set on outgoing respoonses as 'Access-Control-Allow-Origin' and
 * 'Access-Control-Allow-Headers'.
 *
 */
public final class CorsMetadata {

    private final String origin;
    private final String headers;

    /**
     * Create a new instance with 'origin' of any '*' and with no headers.
     */
    public CorsMetadata() {
        this("*", null);
    }

    /**
     * Create a new instance.
     *
     * @param origin the origin to be used. If null, or "null" the origin will be set to any '*'
     * @param headers the headers that should be stored, and later returned as CORS allowed headers.
     */
    public CorsMetadata(final String origin, final String headers) {
        this.origin = origin == null || origin.equals("null") ? "*" : origin;
        this.headers = headers;
    }

    /**
     * Returns the stored origin.
     *
     * @return String the origin.
     */
    public String origin() {
        return origin;
    }

    /**
     * Returns the headers origin.
     *
     * @return String the headers.
     */
    public String headers() {
        return headers;
    }

    /**
     * Determines whether this instance has headers or not.
     *
     * @return true if this instance has headers, false otherwise.
     */
    public boolean hasHeaders() {
        return headers != null && !headers.isEmpty() && !headers.equals(" ");
    }

    @Override
    public String toString() {
        return "CorsMetadata[origin=" + origin + ", headers=" + headers + "]";
    }

}
