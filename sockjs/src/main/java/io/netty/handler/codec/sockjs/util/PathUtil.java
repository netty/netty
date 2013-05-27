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
package io.netty.handler.codec.sockjs.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PathUtil {

    private static final Pattern SERVICE_PATTERN = Pattern.compile(".*/([^/\\?]+)[\\s?]?");

    private PathUtil() {
    }

    public static boolean forService(final String path, final String serviceName) {
        if (path == null) {
            return false;
        }
        ArgumentUtil.checkNotNullAndNotEmpty(serviceName, "serviceName");
        final String prefix = prefix(path);
        return serviceName.equals(prefix);
    }

    public static String prefix(String url) {
        ArgumentUtil.checkNotNullAndNotEmpty(url, "url");
        final Matcher m = SERVICE_PATTERN.matcher(url);
        while (m.find()) {
            return m.group(1);
        }
        return null;
    }

}
