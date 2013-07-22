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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import org.junit.Test;

public class JsonUtilTest {

    @Test
    public void decodeArrayWithSimpleStrings() throws Exception {
        final String[] decode = JsonUtil.decode("[\"one\", \"two\"]");
        assertThat(decode.length, is(2));
        assertThat(decode[0], equalTo("one"));
        assertThat(decode[1], equalTo("two"));
    }

    @Test
    public void decodeArrayWithJsonObject() throws Exception {
        final String[] decode = JsonUtil.decode("[{\"firstName\":\"Fletch\"}]");
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("{\"firstName\":\"Fletch\"}"));
    }

    @Test
    public void decodeArrayWithJsonObjectAndString() throws Exception {
        final String[] decode = JsonUtil.decode("[{\"firstName\":\"Fletch\"}, \"10\"]");
        assertThat(decode.length, is(2));
        assertThat(decode[0], equalTo("{\"firstName\":\"Fletch\"}"));
        assertThat(decode[1], equalTo("10"));
    }

    @Test
    public void decodeArrayWithJsonObjectAndArray() throws Exception {
        final String[] decode = JsonUtil.decode("[{\"firstName\":[\"Fletch\"]}, \"10\"]");
        assertThat(decode.length, is(2));
        assertThat(decode[0], equalTo("{\"firstName\":[\"Fletch\"]}"));
        assertThat(decode[1], equalTo("10"));
    }

    @Test
    public void decodeObject() throws Exception {
        final String[] decode = JsonUtil.decode("{\"firstName\":\"Fletch\"}");
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("{\"firstName\":\"Fletch\"}"));
    }

    @Test
    public void decodeString() throws Exception {
        final String[] decode = JsonUtil.decode("\"x\"");
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("x"));
    }

    @Test
    public void decodeTextWebSocketFrameSimpleString() throws Exception {
        final String[] decode = JsonUtil.decode(new TextWebSocketFrame("\"test\""));
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("test"));
    }

    @Test
    public void decodeTextWebSocketFrameArray() throws Exception {
        final String[] decode = JsonUtil.decode(new TextWebSocketFrame("[\"test\"]"));
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("test"));
    }

    @Test
    public void decodeTextWebSocketFrameObject() throws Exception {
        final String[] decode = JsonUtil.decode(new TextWebSocketFrame("{\"firstName\":\"Fletch\"}"));
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("{\"firstName\":\"Fletch\"}"));
    }

}
