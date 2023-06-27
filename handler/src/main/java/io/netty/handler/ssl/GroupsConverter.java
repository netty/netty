/*
 * Copyright 2021 The Netty Project
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Convert java naming to OpenSSL naming if possible and if not return the original name.
 */
final class GroupsConverter {

    private static final Map<String, String> mappings;

    static {
        // See https://tools.ietf.org/search/rfc4492#appendix-A and https://www.java.com/en/configure_crypto.html
        Map<String, String> map = new HashMap<String, String>();
        map.put("secp224r1", "P-224");
        map.put("prime256v1", "P-256");
        map.put("secp256r1", "P-256");
        map.put("secp384r1", "P-384");
        map.put("secp521r1", "P-521");
        map.put("x25519", "X25519");
        mappings = Collections.unmodifiableMap(map);
    }

    static String toOpenSsl(String key) {
        String mapping = mappings.get(key);
        if (mapping == null) {
            return key;
        }
        return mapping;
    }

    private GroupsConverter() { }
}
