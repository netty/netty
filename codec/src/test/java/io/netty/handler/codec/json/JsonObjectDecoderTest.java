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

package io.netty.handler.codec.json;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonObjectDecoderTest {
    @Test
    public void testJsonObjectOverMultipleWrites() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String objectPart1 = "{ \"firstname\": \"John";
        String objectPart2 = "\" ,\n \"surname\" :";
        String objectPart3 = "\"Doe\", age:22   \n}";

        // Test object
        ch.writeInbound(Unpooled.copiedBuffer("  \n\n  " + objectPart1, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(objectPart2, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(objectPart3 + "   \n\n  \n", CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals(objectPart1 + objectPart2 + objectPart3, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testMultipleJsonObjectsOverMultipleWrites() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String objectPart1 = "{\"name\":\"Jo";
        String objectPart2 = "hn\"}{\"name\":\"John\"}{\"name\":\"Jo";
        String objectPart3 = "hn\"}";

        ch.writeInbound(Unpooled.copiedBuffer(objectPart1, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(objectPart2, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(objectPart3, CharsetUtil.UTF_8));

        for (int i = 0; i < 3; i++) {
            ByteBuf res = ch.readInbound();
            assertEquals("{\"name\":\"John\"}", res.toString(CharsetUtil.UTF_8));
            res.release();
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testJsonArrayOverMultipleWrites() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String arrayPart1 = "[{\"test";
        String arrayPart2 = "case\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"";
        String arrayPart3 = "  }\n\n    , ";
        String arrayPart4 = "{\"testcase\" : \"Streaming string me";
        String arrayPart5 = "ssage\"} ]";

        // Test array
        ch.writeInbound(Unpooled.copiedBuffer("   " + arrayPart1, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(arrayPart2, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(arrayPart3, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(arrayPart4, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(arrayPart5 + "      ", CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals(arrayPart1 + arrayPart2 + arrayPart3 + arrayPart4 + arrayPart5, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testStreamJsonArrayOverMultipleWrites1() {
        String[] array = new String[] {
                "   [{\"test",
                "case\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"",
                "  }\n\n    , ",
                "{\"testcase\" : \"Streaming string me",
                "ssage\"} ]      "
                };
        String[] result = new String[] {
                "{\"testcase\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"  }",
                "{\"testcase\" : \"Streaming string message\"}"
                };
        doTestStreamJsonArrayOverMultipleWrites(2, array, result);
    }

    @Test
    public void testStreamJsonArrayOverMultipleWrites2() {
        String[] array = new String[] {
                "   [{\"test",
                "case\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"",
                "  }\n\n    , {\"test",
                "case\" : \"Streaming string me",
                "ssage\"} ]      "
                };
        String[] result = new String[] {
                "{\"testcase\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"  }",
                "{\"testcase\" : \"Streaming string message\"}"
                };
        doTestStreamJsonArrayOverMultipleWrites(2, array, result);
    }

    @Test
    public void testStreamJsonArrayOverMultipleWrites3() {
        String[] array = new String[] {
                "   [{\"test",
                "case\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"",
                "  }\n\n    , [{\"test",
                "case\" : \"Streaming string me",
                "ssage\"}] ]      "
                };
        String[] result = new String[] {
                "{\"testcase\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"  }",
                "[{\"testcase\" : \"Streaming string message\"}]"
                };
        doTestStreamJsonArrayOverMultipleWrites(2, array, result);
    }

    private static void doTestStreamJsonArrayOverMultipleWrites(int indexDataAvailable,
            String[] array, String[] result) {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder(true));

        boolean dataAvailable = false;
        for (String part : array) {
            dataAvailable = ch.writeInbound(Unpooled.copiedBuffer(part, CharsetUtil.UTF_8));
            if (indexDataAvailable > 0) {
                assertFalse(dataAvailable);
            } else {
                assertTrue(dataAvailable);
            }
            indexDataAvailable--;
        }

        for (String part : result) {
            ByteBuf res = ch.readInbound();
            assertEquals(part, res.toString(CharsetUtil.UTF_8));
            res.release();
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testSingleByteStream() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String json = "{\"foo\" : {\"bar\" : [{},{}]}}";
        for (byte c : json.getBytes(CharsetUtil.UTF_8)) {
            ch.writeInbound(Unpooled.copiedBuffer(new byte[] {c}));
        }

        ByteBuf res = ch.readInbound();
        assertEquals(json, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testBackslashInString1() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        // {"foo" : "bar\""}
        String json = "{\"foo\" : \"bar\\\"\"}";
        System.out.println(json);
        ch.writeInbound(Unpooled.copiedBuffer(json, CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals(json, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testBackslashInString2() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        // {"foo" : "bar\\"}
        String json = "{\"foo\" : \"bar\\\\\"}";
        System.out.println(json);
        ch.writeInbound(Unpooled.copiedBuffer(json, CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals(json, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testBackslashInString3() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        // {"foo" : "bar\\\""}
        String json = "{\"foo\" : \"bar\\\\\\\"\"}";
        System.out.println(json);
        ch.writeInbound(Unpooled.copiedBuffer(json, CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals(json, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testMultipleJsonObjectsInOneWrite() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String object1 = "{\"key\" : \"value1\"}",
               object2 = "{\"key\" : \"value2\"}",
               object3 = "{\"key\" : \"value3\"}";

        ch.writeInbound(Unpooled.copiedBuffer(object1 + object2 + object3, CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals(object1, res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals(object2, res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals(object3, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test(expected = CorruptedFrameException.class)
    public void testNonJsonContent1() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        try {
            ch.writeInbound(Unpooled.copiedBuffer("  b [1,2,3]", CharsetUtil.UTF_8));
        } finally {
            assertFalse(ch.finish());
        }

        fail();
    }

    @Test(expected = CorruptedFrameException.class)
    public void testNonJsonContent2() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("  [1,2,3]  ", CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals("[1,2,3]", res.toString(CharsetUtil.UTF_8));
        res.release();

        try {
            ch.writeInbound(Unpooled.copiedBuffer(" a {\"key\" : 10}", CharsetUtil.UTF_8));
        } finally {
            assertFalse(ch.finish());
        }

        fail();
    }

    @Test (expected = TooLongFrameException.class)
    public void testMaxObjectLength() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder(6));
        try {
            ch.writeInbound(Unpooled.copiedBuffer("[2,4,5]", CharsetUtil.UTF_8));
        } finally {
            assertFalse(ch.finish());
        }

        fail();
    }

    @Test
    public void testOneJsonObjectPerWrite() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String object1 = "{\"key\" : \"value1\"}",
               object2 = "{\"key\" : \"value2\"}",
               object3 = "{\"key\" : \"value3\"}";

        ch.writeInbound(Unpooled.copiedBuffer(object1, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(object2, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(object3, CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals(object1, res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals(object2, res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals(object3, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testSpecialJsonCharsInString() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String object = "{ \"key\" : \"[]{}}\\\"}}'}\"}";
        ch.writeInbound(Unpooled.copiedBuffer(object, CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals(object, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testStreamArrayElementsSimple() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder(Integer.MAX_VALUE, true));

        String array = "[  12, \"bla\"  , 13.4   \t  ,{\"key0\" : [1,2], \"key1\" : 12, \"key2\" : {}} , " +
                       "true, false, null, [\"bla\", {}, [1,2,3]] ]";
        String object = "{\"bla\" : \"blub\"}";
        ch.writeInbound(Unpooled.copiedBuffer(array, CharsetUtil.UTF_8));
        ch.writeInbound(Unpooled.copiedBuffer(object, CharsetUtil.UTF_8));

        ByteBuf res = ch.readInbound();
        assertEquals("12", res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals("\"bla\"", res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals("13.4", res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals("{\"key0\" : [1,2], \"key1\" : 12, \"key2\" : {}}", res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals("true", res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals("false", res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals("null", res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals("[\"bla\", {}, [1,2,3]]", res.toString(CharsetUtil.UTF_8));
        res.release();
        res = ch.readInbound();
        assertEquals(object, res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }

    @Test
    public void testCorruptedFrameException() {
        final String part1 = "{\"a\":{\"b\":{\"c\":{ \"d\":\"27301\", \"med\":\"d\", \"path\":\"27310\"} }," +
                " \"status\":\"OK\" } }{\"";
        final String part2 = "a\":{\"b\":{\"c\":{\"ory\":[{\"competi\":[{\"event\":[{" + "\"externalI\":{\"external\"" +
                ":[{\"id\":\"O\"} ]";

        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        ByteBuf res;

        ch.writeInbound(Unpooled.copiedBuffer(part1, CharsetUtil.UTF_8));
        res = ch.readInbound();
        assertEquals("{\"a\":{\"b\":{\"c\":{ \"d\":\"27301\", \"med\":\"d\", \"path\":\"27310\"} }, " +
                "\"status\":\"OK\" } }", res.toString(CharsetUtil.UTF_8));
        res.release();

        ch.writeInbound(Unpooled.copiedBuffer(part2, CharsetUtil.UTF_8));
        res = ch.readInbound();

        assertNull(res);

        ch.writeInbound(Unpooled.copiedBuffer("}}]}]}]}}}}", CharsetUtil.UTF_8));
        res = ch.readInbound();

        assertEquals("{\"a\":{\"b\":{\"c\":{\"ory\":[{\"competi\":[{\"event\":[{" + "\"externalI\":{" +
                "\"external\":[{\"id\":\"O\"} ]}}]}]}]}}}}", res.toString(CharsetUtil.UTF_8));
        res.release();

        assertFalse(ch.finish());
    }
}
