/*
 * Copyright 2013 The Netty Project
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

import com.fasterxml.jackson.databind.JsonMappingException;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import org.junit.Test;


public class JsonConverterTest {

    @Test
    public void decodeArrayWithSimpleStrings() throws Exception {
        final String[] decode = JsonConverter.decode("[\"one\", \"two\"]");
        assertThat(decode.length, is(2));
        assertThat(decode[0], equalTo("one"));
        assertThat(decode[1], equalTo("two"));
    }

    @Test
    public void decodeArrayWithJsonObject() throws Exception {
        final String[] decode = JsonConverter.decode("[{\"firstName\":\"Fletch\"}]");
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("{\"firstName\":\"Fletch\"}"));
    }

    @Test
    public void decodeArrayWithJsonObjectAndString() throws Exception {
        final String[] decode = JsonConverter.decode("[{\"firstName\":\"Fletch\"}, \"10\"]");
        assertThat(decode.length, is(2));
        assertThat(decode[0], equalTo("{\"firstName\":\"Fletch\"}"));
        assertThat(decode[1], equalTo("10"));
    }

    @Test
    public void decodeArrayWithJsonObjectAndArray() throws Exception {
        final String[] decode = JsonConverter.decode("[{\"firstName\":[\"Fletch\"]}, \"10\"]");
        assertThat(decode.length, is(2));
        assertThat(decode[0], equalTo("{\"firstName\":[\"Fletch\"]}"));
        assertThat(decode[1], equalTo("10"));
    }

    @Test
    public void decodeObject() throws Exception {
        final String[] decode = JsonConverter.decode("{\"firstName\":\"Fletch\"}");
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("{\"firstName\":\"Fletch\"}"));
    }

    @Test
    public void decodeString() throws Exception {
        final String[] decode = JsonConverter.decode("\"x\"");
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("x"));
    }

    @Test
    public void decodeTextWebSocketFrameSimpleString() throws Exception {
        final TextWebSocketFrame frame = new TextWebSocketFrame("\"test\"");
        final String[] decode = JsonConverter.decode(frame);
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("test"));
        frame.release();
    }

    @Test
    public void decodeTextWebSocketFrameArray() throws Exception {
        final TextWebSocketFrame frame = new TextWebSocketFrame("[\"test\"]");
        final String[] decode = JsonConverter.decode(frame);
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("test"));
        frame.release();
    }

    @Test
    public void decodeTextWebSocketFrameObject() throws Exception {
        final TextWebSocketFrame frame = new TextWebSocketFrame("{\"firstName\":\"Fletch\"}");
        final String[] decode = JsonConverter.decode(frame);
        assertThat(decode.length, is(1));
        assertThat(decode[0], equalTo("{\"firstName\":\"Fletch\"}"));
        frame.release();
    }

    @Test
    public void controlCharacters() {
        assertThat(JsonConverter.isControlCharacter('\u0000'), is(true));
        assertThat(JsonConverter.isControlCharacter('\u0001'), is(true));
        assertThat(JsonConverter.isControlCharacter('\u001F'), is(true));
        assertThat(JsonConverter.isControlCharacter('\u002F'), is(false));
    }

    @Test
    public void isFormatControlCharacter() {
        assertThat(JsonConverter.isFormatControlCharacter('\u200C'), is(true));
        assertThat(JsonConverter.isFormatControlCharacter('\u200D'), is(true));
        assertThat(JsonConverter.isFormatControlCharacter('\u200B'), is(false));
        assertThat(JsonConverter.isFormatControlCharacter('\u200E'), is(false));
    }

    @Test
    public void isSeparatorCharacters() {
        assertThat(JsonConverter.isSeparatorCharacter('\u2028'), is(true));
        assertThat(JsonConverter.isSeparatorCharacter('\u2029'), is(true));
        assertThat(JsonConverter.isSeparatorCharacter('\u202A'), is(true));
        assertThat(JsonConverter.isSeparatorCharacter('\u202B'), is(true));
        assertThat(JsonConverter.isSeparatorCharacter('\u202C'), is(true));
        assertThat(JsonConverter.isSeparatorCharacter('\u202D'), is(true));
        assertThat(JsonConverter.isSeparatorCharacter('\u202E'), is(true));
        assertThat(JsonConverter.isSeparatorCharacter('\u202F'), is(true));
        assertThat(JsonConverter.isSeparatorCharacter('\u2027'), is(false));
        assertThat(JsonConverter.isSeparatorCharacter('\u2030'), is(false));
    }

    @Test
    public void escapeSpecialCharacters() throws JsonMappingException {
        final String chars = "\u0061bcd\uFFFF";
        final String escaped = JsonConverter.escapeCharacters(chars.toCharArray());
        assertThat(escaped, equalTo("abcd\\uffff"));
    }

}
