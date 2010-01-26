/*
 * Copyright 2010 Red Hat, Inc.
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
package org.jboss.netty.handler.codec.rtsp;

import java.util.HashMap;
import java.util.Map;

import org.jboss.netty.handler.codec.http.HttpMethod;

/**
 * The request method of RTSP.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Amit Bhayani (amit.bhayani@gmail.com)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class RtspMethod extends HttpMethod {

    /**
     * The DESCRIBE method retrieves the description of a presentation or
     * media object identified by the request URL from a server.
     */
    public static final RtspMethod DESCRIBE = new RtspMethod("DESCRIBE");

    /**
     * The ANNOUNCE posts the description of a presentation or media object
     * identified by the request URL to a server, or updates the client-side
     * session description in real-time.
     */
    public static final RtspMethod ANNOUNCE = new RtspMethod("ANNOUNCE");

    /**
     * The SETUP request for a URI specifies the transport mechanism to be
     * used for the streamed media.
     */
    public static final RtspMethod SETUP = new RtspMethod("SETUP");

    /**
     * The PLAY method tells the server to start sending data via the
     * mechanism specified in SETUP.
     */
    public static final RtspMethod PLAY = new RtspMethod("PLAY");

    /**
     * The PAUSE request causes the stream delivery to be interrupted
     * (halted) temporarily.
     */
    public static final RtspMethod PAUSE = new RtspMethod("PAUSE");

    /**
     * The TEARDOWN request stops the stream delivery for the given URI,
     * freeing the resources associated with it.
     */
    public static final RtspMethod TEARDOWN = new RtspMethod("TEARDOWN");

    /**
     * The GET_PARAMETER request retrieves the value of a parameter of a
     * presentation or stream specified in the URI.
     */
    public static final RtspMethod GET_PARAMETER = new RtspMethod("GET_PARAMETER");

    /**
     * The SET_PARAMETER requests to set the value of a parameter for a
     * presentation or stream specified by the URI.
     */
    public static final RtspMethod SET_PARAMETER = new RtspMethod("SET_PARAMETER");

    /**
     * The REDIRECT request informs the client that it must connect to another
     * server location.
     */
    public static final RtspMethod REDIRECT = new RtspMethod("REDIRECT");

    /**
     * The RECORD method initiates recording a range of media data according to
     * the presentation description.
     */
    public static final RtspMethod RECORD = new RtspMethod("RECORD");

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
     * Creates a new RTSP method with the specified name.
     */
    public RtspMethod(String name) {
        super(name);
    }

    /**
     * Returns the {@link HttpMethod} represented by the specified name.
     * If the specified name is a standard RTSP method name, a cached instance
     * will be returned.  Otherwise, a new instance will be returned.
     * Please note that this method does not return {@link RtspMethod} but
     * returns {@link HttpMethod} because the RTSP re-uses some HTTP methods
     * in its specification.
     */
    public static HttpMethod valueOf(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        name = name.trim().toUpperCase();
        if (name.length() == 0) {
            throw new IllegalArgumentException("empty name");
        }

        HttpMethod result = methodMap.get(name);
        if (result != null) {
            return result;
        } else {
            return new RtspMethod(name);
        }
    }

}
