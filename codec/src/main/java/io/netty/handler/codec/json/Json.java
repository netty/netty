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
package io.netty.handler.codec.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Json {
    private static volatile ObjectMapper objectMapper;
    private static final ObjectMapper defaultObjectMapper = new ObjectMapper();

    private Json() {
    }

    /**
     * Convert a JsonNode to a Java value
     *
     * @param jsonNode JsonNode to convert.
     * @param clazz Expected Java value type.
     */
    public static <T> T fromJsonNode(final JsonNode jsonNode, final Class<T> clazz) {
        try {
            return getObjectMapper().treeToValue(jsonNode, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert an object to JsonNode.
     *
     * @param object Value to convert.
     */
    public static JsonNode toJsonNode(final Object object) {
        try {
            return getObjectMapper().valueToTree(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper == null ? defaultObjectMapper : objectMapper;
    }

    public static void setObjectMapper(ObjectMapper objectMapper) {
        Json.objectMapper = objectMapper;
    }
}
