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

import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.TextHeaderProcessor;
import io.netty.handler.codec.TextHeaders;

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
    StompHeaders add(CharSequence name, Object value);

    @Override
    StompHeaders add(CharSequence name, Iterable<?> values);

    @Override
    StompHeaders add(CharSequence name, Object... values);

    @Override
    StompHeaders add(TextHeaders headers);

    @Override
    StompHeaders set(CharSequence name, Object value);

    @Override
    StompHeaders set(CharSequence name, Iterable<?> values);

    @Override
    StompHeaders set(CharSequence name, Object... values);

    @Override
    StompHeaders set(TextHeaders headers);

    @Override
    StompHeaders clear();

    @Override
    StompHeaders forEachEntry(TextHeaderProcessor processor);
}
