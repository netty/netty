/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.sockjs.util;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for handling parsing and validation of a callback request
 * query parameter.
 *
 */
public final class Callbacks {

    public static final String CALLBACK_REQUIRED = "\"callback\" parameter required";
    public static final String CALLBACK_INVALID = "invalid \"callback\" parameter";
    private static final Pattern INVALID_CALLBACK_PATTERN = Pattern.compile("[^a-zA-Z0-9-_.]");

    private Callbacks() {
    }

    /**
     * Checks if the passed-in callback request parameter is invalid.
     *
     * @param callbackParam the callback parameter to check.
     * @return {@code true} if the callback is invalid.
     */
    public static boolean invalid(final String callbackParam) {
        return callbackParam.isEmpty() || INVALID_CALLBACK_PATTERN.matcher(callbackParam).find();
    }

    /**
     * Extracts a callback query parameter named 'c' if one exist in the request.
     *
     * @param request the {@link HttpRequest} to parse.
     * @return {@code String} which is either empty is the query param did not exist for the value of it.
     */
    public static String parse(final HttpRequest request) {
        final QueryStringDecoder qsd = new QueryStringDecoder(request.uri());
        final List<String> c = qsd.parameters().get("c");
        return c == null || c.isEmpty() ? "" : c.get(0);
    }

    /**
     * Returns the appropriate error message for a missing vs an invalid callback query parameter.
     *
     * @param callbackParam the request query callback parameter
     * @return {@code String} either {@link #CALLBACK_REQUIRED} or {@link #CALLBACK_INVALID} depending on if the
     * callback was empty/missing vs if is was invalid.
     */
    public static String errorMsg(final String callbackParam) {
        return callbackParam == null || callbackParam.isEmpty() ? CALLBACK_REQUIRED : CALLBACK_INVALID;
    }

}
