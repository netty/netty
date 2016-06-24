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

public class GsonEncoderTest {

    @Test
    public void testEncodeString() {
        EmbeddedChannel ch = new EmbeddedChannel(new GsonEncoder<String>(String.class));
        ch.writeOutbound("netty");
        String json = "\"netty\"";
        assertEquals(json, ch.readOutbound());
        assertFalse(ch.finish());
    }

    @Test
    public void testEncodingMap() {
        EmbeddedChannel ch = new EmbeddedChannel(new GsonEncoder<Map>(Map.class));
        Map<String, String> map = Collections.singletonMap("firstName", "John");
        ch.writeOutbound(map);
        String json = "{\"firstName\":\"John\"}";
        assertEquals(json, ch.readOutbound());
        assertFalse(ch.finish());
    }
}
