/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.http.router;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.util.Map;

/**
 * The path can contain constants or placeholders, example:
 * {@code constant1/:placeholder1/constant2/:*}.
 * {@code :*} is a special placeholder to catch the rest of the path
 * (may include slashes). If exists, it must appear at the end of the path.
 *
 * The path must not contain URL query, example:
 * {@code constant1/constant2?foo=bar}.
 *
 * The path will be broken to tokens, example:
 * {@code ["constant1", ":variable", "constant2", ":*"]}
 */
final class Path {
    public static String removeSlashesAtBothEnds(String path) {
        ObjectUtil.checkNotNull(path, "path");

        if (path.isEmpty()) {
            return path;
        }

        int beginIndex = 0;
        while (beginIndex < path.length() && path.charAt(beginIndex) == '/') {
            beginIndex++;
        }
        if (beginIndex == path.length()) {
            return StringUtil.EMPTY_STRING;
        }

        int endIndex = path.length() - 1;
        while (endIndex > beginIndex && path.charAt(endIndex) == '/') {
            endIndex--;
        }

        return path.substring(beginIndex, endIndex + 1);
    }

    //--------------------------------------------------------------------------

    private final String   path;
    private final String[] tokens;

    /**
     * The path must not contain URL query, example:
     * {@code constant1/constant2?foo=bar}.
     *
     * The path will be stored without slashes at both ends.
     */
    public Path(String path) {
        this.path   = removeSlashesAtBothEnds(ObjectUtil.checkNotNull(path, "path"));
        this.tokens = StringUtil.split(this.path, '/');
    }

    /** Returns the path given at the constructor, without slashes at both ends. */
    public String path() {
        return path;
    }

    /**
     * Returns the path given at the constructor, without slashes at both ends,
     * and split by {@code '/'}.
     */
    public String[] tokens() {
        return tokens;
    }

    //--------------------------------------------------------------------------
    // Need these so that Paths can be conveniently used as Map keys.

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        return ((Path) o).path.equals(path);
    }

    //--------------------------------------------------------------------------

    /**
     * {@code params} will be updated with params embedded in the path.
     *
     * This method signature is designed so that {@code pathTokens} and {@code params}
     * can be created only once then reused, to optimize for performance when a
     * large number of paths need to be matched.
     *
     * @return {@code false} if not matched; in this case params should be reset
     */
    public boolean match(String[] requestPathTokens, Map<String, String> params) {
        if (tokens.length == requestPathTokens.length) {
            for (int i = 0; i < tokens.length; i++) {
                String key   = tokens[i];
                String value = requestPathTokens[i];

                if (key.length() > 0 && key.charAt(0) == ':') {
                    // This is a placeholder
                    params.put(key.substring(1), value);
                } else if (!key.equals(value)) {
                    // This is a constant
                    return false;
                }
            }

            return true;
        }

        if (tokens.length > 0 &&
            tokens[tokens.length - 1].equals(":*") &&
            tokens.length <= requestPathTokens.length) {
            // The first part
            for (int i = 0; i < tokens.length - 2; i++) {
                String key   = tokens[i];
                String value = requestPathTokens[i];

                if (key.length() > 0 && key.charAt(0) == ':') {
                    // This is a placeholder
                    params.put(key.substring(1), value);
                } else if (!key.equals(value)) {
                    // This is a constant
                    return false;
                }
            }

            // The last :* part
            StringBuilder b = new StringBuilder(requestPathTokens[tokens.length - 1]);
            for (int i = tokens.length; i < requestPathTokens.length; i++) {
                b.append('/');
                b.append(requestPathTokens[i]);
            }
            params.put("*", b.toString());

            return true;
        }

        return false;
    }
}
