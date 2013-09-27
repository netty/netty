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
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class JsonTest {
    @Test
    public void testFromJsonNode() throws Exception {
        Foo foo = new Foo().setToDefaultValue();
        JsonNode jsonNode = Json.getObjectMapper().valueToTree(foo);
        assertThat(Json.toJsonNode(foo), is(jsonNode));
    }

    @Test
    public void testToJsonNode() throws Exception {
        Foo foo = new Foo().setToDefaultValue();
        JsonNode jsonNode = Json.getObjectMapper().valueToTree(foo);
        assertThat(Json.fromJsonNode(jsonNode, Foo.class), is(foo));
    }
}
