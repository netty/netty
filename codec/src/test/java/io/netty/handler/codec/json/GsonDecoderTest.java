/*
 * Copyright 2016 The Netty Project
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

import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GsonDecoderTest {

    @Test
    public void testDecodeString() {
        EmbeddedChannel ch = new EmbeddedChannel(new GsonDecoder<String>(String.class));
        String json = "\"netty\"";
        ch.writeInbound(json);
        assertEquals("netty", ch.readInbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testDecodeMap() {
        EmbeddedChannel ch = new EmbeddedChannel(new GsonDecoder<Map>(Map.class));
        String json = "{\"firstName\": \"John\"}";
        ch.writeInbound(json);
        assertEquals(Collections.singletonMap("firstName", "John"), ch.readInbound());
        assertFalse(ch.finish());
    }
}
