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
import io.netty.util.CharsetUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * The request getMethod of HTTP or its derived protocols, such as
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 */
public class HttpMethod implements Comparable<HttpMethod> {
    /**
     * The OPTIONS getMethod represents a request for information about the communication options
     * available on the request/response chain identified by the Request-URI. This getMethod allows
     * the client to determine the options and/or requirements associated with a resource, or the
     * capabilities of a server, without implying a resource action or initiating a resource
     * retrieval.
     */
    public static final HttpMethod OPTIONS = new HttpMethod("OPTIONS", true);

    /**
     * The GET getMethod means retrieve whatever information (in the form of an entity) is identified
     * by the Request-URI.  If the Request-URI refers to a data-producing process, it is the
     * produced data which shall be returned as the entity in the response and not the source text
     * of the process, unless that text happens to be the output of the process.
     */
    public static final HttpMethod GET = new HttpMethod("GET", true);

    /**
     * The HEAD getMethod is identical to GET except that the server MUST NOT return a message-body
     * in the response.
     */
    public static final HttpMethod HEAD = new HttpMethod("HEAD", true);

    /**
     * The POST getMethod is used to request that the origin server accept the entity enclosed in the
     * request as a new subordinate of the resource identified by the Request-URI in the
     * Request-Line.
     */
    public static final HttpMethod POST = new HttpMethod("POST", true);

    /**
     * The PUT getMethod requests that the enclosed entity be stored under the supplied Request-URI.
     */
    public static final HttpMethod PUT = new HttpMethod("PUT", true);

    /**
     * The PATCH getMethod requests that a set of changes described in the
     * request entity be applied to the resource identified by the Request-URI.
     */
    public static final HttpMethod PATCH = new HttpMethod("PATCH", true);

    /**
     * The DELETE getMethod requests that the origin server delete the resource identified by the
     * Request-URI.
     */
    public static final HttpMethod DELETE = new HttpMethod("DELETE", true);

    /**
     * The TRACE getMethod is used to invoke a remote, application-layer loop- back of the request
     * message.
     */
    public static final HttpMethod TRACE = new HttpMethod("TRACE", true);

    /**
     * This specification reserves the getMethod name CONNECT for use with a proxy that can dynamically
     * switch to being a tunnel
     */
    public static final HttpMethod CONNECT = new HttpMethod("CONNECT", true);

    private static final Map<String, HttpMethod> methodMap =
            new HashMap<String, HttpMethod>();

    static {
        methodMap.put(OPTIONS.toString(), OPTIONS);
        methodMap.put(GET.toString(), GET);
        methodMap.put(HEAD.toString(), HEAD);
        methodMap.put(POST.toString(), POST);
        methodMap.put(PUT.toString(), PUT);
        methodMap.put(PATCH.toString(), PATCH);
        methodMap.put(DELETE.toString(), DELETE);
        methodMap.put(TRACE.toString(), TRACE);
        methodMap.put(CONNECT.toString(), CONNECT);
    }

    /**
     * Returns the {@link HttpMethod} represented by the specified name.
     * If the specified name is a standard HTTP getMethod name, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     */
    public static HttpMethod valueOf(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }

        HttpMethod result = methodMap.get(name);
        if (result != null) {
            return result;
        } else {
            return new HttpMethod(name);
        }
    }

    private final String name;
    private final byte[] bytes;

    /**
     * Creates a new HTTP getMethod with the specified name.  You will not need to
     * create a new getMethod unless you are implementing a protocol derived from
     * HTTP, such as
     * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
     * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>
     */
    public HttpMethod(String name) {
        this(name, false);
    }

    private HttpMethod(String name, boolean bytes) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        name = name.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("empty name");
        }

        for (int i = 0; i < name.length(); i ++) {
            if (Character.isISOControl(name.charAt(i)) ||
                Character.isWhitespace(name.charAt(i))) {
                throw new IllegalArgumentException("invalid character in name");
            }
        }

        this.name = name;
        if (bytes) {
            this.bytes = name.getBytes(CharsetUtil.US_ASCII);
        } else {
            this.bytes = null;
        }
    }

    /**
     * Returns the name of this getMethod.
     */
    public String name() {
        return name;
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpMethod)) {
            return false;
        }

        HttpMethod that = (HttpMethod) o;
        return name().equals(that.name());
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int compareTo(HttpMethod o) {
        return name().compareTo(o.name());
    }

    void encode(ByteBuf buf) {
        if (bytes == null) {
            HttpHeaders.encodeAscii0(name, buf);
        } else {
            buf.writeBytes(bytes);
        }
    }
}
