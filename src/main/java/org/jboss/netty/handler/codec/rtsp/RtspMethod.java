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
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Amit Bhayani (amit.bhayani@gmail.com)
 * @author Trustin Lee (trustin@gmail.com)
 * @version $Rev$, $Date$
 */
public class RtspMethod extends HttpMethod {

    public static final RtspMethod DESCRIBE = new RtspMethod("DESCRIBE");

    public static final RtspMethod ANNOUNCE = new RtspMethod("ANNOUNCE");

    public static final RtspMethod GET_PARAMETER = new RtspMethod(
            "GET_PARAMETER");

    public static final RtspMethod OPTIONS = new RtspMethod("OPTIONS");

    public static final RtspMethod PAUSE = new RtspMethod("PAUSE");

    public static final RtspMethod PLAY = new RtspMethod("PLAY");

    public static final RtspMethod RECORD = new RtspMethod("RECORD");

    public static final RtspMethod REDIRECT = new RtspMethod("REDIRECT");

    public static final RtspMethod SETUP = new RtspMethod("SETUP");

    public static final RtspMethod SET_PARAMETER = new RtspMethod(
            "SET_PARAMETER");

    public static final RtspMethod TEARDOWN = new RtspMethod("TEARDOWN");

    private static final Map<String, RtspMethod> methodMap = new HashMap<String, RtspMethod>();

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

    public RtspMethod(String name) {
        super(name);
    }

    public static RtspMethod valueOf(String name) {

        if (name == null) {
            throw new NullPointerException("name");
        }

        name = name.trim().toUpperCase();
        if (name.length() == 0) {
            throw new IllegalArgumentException("empty name");
        }

        RtspMethod result = methodMap.get(name);
        if (result != null) {
            return result;
        } else {
            return new RtspMethod(name);
        }
    }

}
