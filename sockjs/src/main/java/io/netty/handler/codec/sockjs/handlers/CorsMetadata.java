/*
 * Copyright 2012 The Netty Project
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

public class CorsMetadata {

    private String origin;
    private String headers;

    public CorsMetadata() {
        this("*", null);
    }

    public CorsMetadata(final String origin, final String headers) {
        this.origin = origin == null || origin.equals("null") ? "*" : origin;
        this.headers = headers;
    }

    public String origin() {
        return origin;
    }

    public String headers() {
        return headers;
    }

    public boolean hasHeaders() {
        return headers != null && !headers.isEmpty() && !headers.equals(" ");
    }

    @Override
    public String toString() {
        return "CorsMetadata[origin=" + origin + ", headers=" + headers + "]";
    }

}
