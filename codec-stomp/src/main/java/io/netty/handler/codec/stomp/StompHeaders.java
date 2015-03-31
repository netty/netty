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
package io.netty.handler.codec.stomp;

import io.netty.handler.codec.TextHeaders;
import io.netty.util.AsciiString;

/**
 * The multimap data structure for the STOMP header names and values. It also provides the constants for the standard
 * STOMP header names and values.
 */
public interface StompHeaders extends TextHeaders {

    AsciiString ACCEPT_VERSION = new AsciiString("accept-version");
    AsciiString HOST = new AsciiString("host");
    AsciiString LOGIN = new AsciiString("login");
    AsciiString PASSCODE = new AsciiString("passcode");
    AsciiString HEART_BEAT = new AsciiString("heart-beat");
    AsciiString VERSION = new AsciiString("version");
    AsciiString SESSION = new AsciiString("session");
    AsciiString SERVER = new AsciiString("server");
    AsciiString DESTINATION = new AsciiString("destination");
    AsciiString ID = new AsciiString("id");
    AsciiString ACK = new AsciiString("ack");
    AsciiString TRANSACTION = new AsciiString("transaction");
    AsciiString RECEIPT = new AsciiString("receipt");
    AsciiString MESSAGE_ID = new AsciiString("message-id");
    AsciiString SUBSCRIPTION = new AsciiString("subscription");
    AsciiString RECEIPT_ID = new AsciiString("receipt-id");
    AsciiString MESSAGE = new AsciiString("message");
    AsciiString CONTENT_LENGTH = new AsciiString("content-length");
    AsciiString CONTENT_TYPE = new AsciiString("content-type");

    @Override
    StompHeaders add(CharSequence name, CharSequence value);

    @Override
    StompHeaders add(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    StompHeaders add(CharSequence name, CharSequence... values);

    @Override
    StompHeaders addObject(CharSequence name, Object value);

    @Override
    StompHeaders addObject(CharSequence name, Iterable<?> values);

    @Override
    StompHeaders addObject(CharSequence name, Object... values);

    @Override
    StompHeaders addBoolean(CharSequence name, boolean value);

    @Override
    StompHeaders addByte(CharSequence name, byte value);

    @Override
    StompHeaders addChar(CharSequence name, char value);

    @Override
    StompHeaders addShort(CharSequence name, short value);

    @Override
    StompHeaders addInt(CharSequence name, int value);

    @Override
    StompHeaders addLong(CharSequence name, long value);

    @Override
    StompHeaders addFloat(CharSequence name, float value);

    @Override
    StompHeaders addDouble(CharSequence name, double value);

    @Override
    StompHeaders addTimeMillis(CharSequence name, long value);

    @Override
    StompHeaders add(TextHeaders headers);

    @Override
    StompHeaders set(CharSequence name, CharSequence value);

    @Override
    StompHeaders set(CharSequence name, Iterable<? extends CharSequence> values);

    @Override
    StompHeaders set(CharSequence name, CharSequence... values);

    @Override
    StompHeaders setObject(CharSequence name, Object value);

    @Override
    StompHeaders setObject(CharSequence name, Iterable<?> values);

    @Override
    StompHeaders setObject(CharSequence name, Object... values);

    @Override
    StompHeaders setBoolean(CharSequence name, boolean value);

    @Override
    StompHeaders setByte(CharSequence name, byte value);

    @Override
    StompHeaders setChar(CharSequence name, char value);

    @Override
    StompHeaders setShort(CharSequence name, short value);

    @Override
    StompHeaders setInt(CharSequence name, int value);

    @Override
    StompHeaders setLong(CharSequence name, long value);

    @Override
    StompHeaders setFloat(CharSequence name, float value);

    @Override
    StompHeaders setDouble(CharSequence name, double value);

    @Override
    StompHeaders setTimeMillis(CharSequence name, long value);

    @Override
    StompHeaders set(TextHeaders headers);

    @Override
    StompHeaders setAll(TextHeaders headers);

    @Override
    StompHeaders clear();
}
