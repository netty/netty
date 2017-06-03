/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.routing;

public class Pattern<T> {
    private final String   path;
    private final String[] tokens;
    private final T        target;

    public static String removeSlashAtBothEnds(String path) {
        if (path.isEmpty()) { return path; }

        int beginIndex = 0;
        while (beginIndex < path.length() && path.charAt(beginIndex) == '/') { beginIndex++; }
        if (beginIndex == path.length()) { return ""; }

        int endIndex = path.length() - 1;
        while (endIndex > beginIndex && path.charAt(endIndex) == '/') { endIndex--; }

        return path.substring(beginIndex, endIndex + 1);
    }

    public Pattern(String path, T target) {
        this.path   = removeSlashAtBothEnds(path);
        this.tokens = this.path.split("/");
        this.target = target;
    }

    public String path() {
        return path;
    }

    public String[] tokens() {
        return tokens;
    }

    public T target() {
        return target;
    }
}
