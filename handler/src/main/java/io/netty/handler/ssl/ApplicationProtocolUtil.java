/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for application protocol common operations.
 */
final class ApplicationProtocolUtil {
    private static final int DEFAULT_LIST_SIZE = 2;

    private ApplicationProtocolUtil() {
    }

    static List<String> toList(Iterable<String> protocols) {
        return toList(DEFAULT_LIST_SIZE, protocols);
    }

    static List<String> toList(int initialListSize, Iterable<String> protocols) {
        if (protocols == null) {
            return null;
        }

        List<String> result = new ArrayList<String>(initialListSize);
        for (String p : protocols) {
            if (p == null || p.isEmpty()) {
                throw new IllegalArgumentException("protocol cannot be null or empty");
            }
            result.add(p);
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("protocols cannot empty");
        }

        return result;
    }

    static List<String> toList(String... protocols) {
        return toList(DEFAULT_LIST_SIZE, protocols);
    }

    static List<String> toList(int initialListSize, String... protocols) {
        if (protocols == null) {
            return null;
        }

        List<String> result = new ArrayList<String>(initialListSize);
        for (String p : protocols) {
            if (p == null || p.isEmpty()) {
                throw new IllegalArgumentException("protocol cannot be null or empty");
            }
            result.add(p);
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("protocols cannot empty");
        }

        return result;
    }
}
