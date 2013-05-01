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
package io.netty.handler.codec.rtsp;

import io.netty.handler.codec.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * The request getMethod of RTSP.
 */
public final class RtspMethods {

    /**
     * The OPTIONS getMethod represents a request for information about the communication options
     * available on the request/response chain identified by the Request-URI. This getMethod allows
     * the client to determine the options and/or requirements associated with a resource, or the
     * capabilities of a server, without implying a resource action or initiating a resource
     * retrieval.
     */
    public static final HttpMethod OPTIONS = HttpMethod.OPTIONS;

    /**
     * The DESCRIBE getMethod retrieves the description of a presentation or
     * media object identified by the request URL from a server.
     */
    public static final HttpMethod DESCRIBE = new HttpMethod("DESCRIBE");

    /**
     * The ANNOUNCE posts the description of a presentation or media object
     * identified by the request URL to a server, or updates the client-side
     * session description in real-time.
     */
    public static final HttpMethod ANNOUNCE = new HttpMethod("ANNOUNCE");

    /**
     * The SETUP request for a URI specifies the transport mechanism to be
     * used for the streamed media.
     */
    public static final HttpMethod SETUP = new HttpMethod("SETUP");

    /**
     * The PLAY getMethod tells the server to start sending data via the
     * mechanism specified in SETUP.
     */
    public static final HttpMethod PLAY = new HttpMethod("PLAY");

    /**
     * The PAUSE request causes the stream delivery to be interrupted
     * (halted) temporarily.
     */
    public static final HttpMethod PAUSE = new HttpMethod("PAUSE");

    /**
     * The TEARDOWN request stops the stream delivery for the given URI,
     * freeing the resources associated with it.
     */
    public static final HttpMethod TEARDOWN = new HttpMethod("TEARDOWN");

    /**
     * The GET_PARAMETER request retrieves the value of a parameter of a
     * presentation or stream specified in the URI.
     */
    public static final HttpMethod GET_PARAMETER = new HttpMethod("GET_PARAMETER");

    /**
     * The SET_PARAMETER requests to set the value of a parameter for a
     * presentation or stream specified by the URI.
     */
    public static final HttpMethod SET_PARAMETER = new HttpMethod("SET_PARAMETER");

    /**
     * The REDIRECT request informs the client that it must connect to another
     * server location.
     */
    public static final HttpMethod REDIRECT = new HttpMethod("REDIRECT");

    /**
     * The RECORD getMethod initiates recording a range of media data according to
     * the presentation description.
     */
    public static final HttpMethod RECORD = new HttpMethod("RECORD");

    private static final Map<String, HttpMethod> methodMap = new HashMap<String, HttpMethod>();

    static {
        methodMap.put(DESCRIBE.toString(), DESCRIBE);
        methodMap.put(ANNOUNCE.toString(), ANNOUNCE);
        methodMap.put(GET_PARAMETER.toString(), GET_PARAMETER);
        methodMap.put(OPTIONS.toString(), OPTIONS);
        methodMap.put(PAUSE.toString(), PAUSE);
        methodMap.put(PLAY.toString(), PLAY);
        methodMap.put(RECORD.toString(), RECORD);
        methodMap.put(REDIRECT.toString(), REDIRECT);
        methodMap.put(SETUP.toString(), SETUP);
        methodMap.put(SET_PARAMETER.toString(), SET_PARAMETER);
        methodMap.put(TEARDOWN.toString(), TEARDOWN);
    }

    /**
     * Returns the {@link HttpMethod} represented by the specified name.
     * If the specified name is a standard RTSP getMethod name, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     */
    public static HttpMethod valueOf(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        name = name.trim().toUpperCase();
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

    private RtspMethods() {
    }
}
