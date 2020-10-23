/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import io.netty.handler.codec.Headers;
import io.netty.util.AsciiString;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/**
 * The multimap data structure for the STOMP header names and values. It also provides the constants for the standard
 * STOMP header names and values.
 */
public interface StompHeaders extends Headers<CharSequence, CharSequence, StompHeaders> {

    AsciiString ACCEPT_VERSION = AsciiString.cached("accept-version");
    AsciiString HOST = AsciiString.cached("host");
    AsciiString LOGIN = AsciiString.cached("login");
    AsciiString PASSCODE = AsciiString.cached("passcode");
    AsciiString HEART_BEAT = AsciiString.cached("heart-beat");
    AsciiString VERSION = AsciiString.cached("version");
    AsciiString SESSION = AsciiString.cached("session");
    AsciiString SERVER = AsciiString.cached("server");
    AsciiString DESTINATION = AsciiString.cached("destination");
    AsciiString ID = AsciiString.cached("id");
    AsciiString ACK = AsciiString.cached("ack");
    AsciiString TRANSACTION = AsciiString.cached("transaction");
    AsciiString RECEIPT = AsciiString.cached("receipt");
    AsciiString MESSAGE_ID = AsciiString.cached("message-id");
    AsciiString SUBSCRIPTION = AsciiString.cached("subscription");
    AsciiString RECEIPT_ID = AsciiString.cached("receipt-id");
    AsciiString MESSAGE = AsciiString.cached("message");
    AsciiString CONTENT_LENGTH = AsciiString.cached("content-length");
    AsciiString CONTENT_TYPE = AsciiString.cached("content-type");

    /**
     * {@link Headers#get(Object)} and convert the result to a {@link String}.
     * @param name the name of the header to retrieve
     * @return the first header value if the header is found. {@code null} if there's no such header.
     */
    String getAsString(CharSequence name);

    /**
     * {@link Headers#getAll(Object)} and convert each element of {@link List} to a {@link String}.
     * @param name the name of the header to retrieve
     * @return a {@link List} of header values or an empty {@link List} if no values are found.
     */
    List<String> getAllAsString(CharSequence name);

    /**
     * {@link #iterator()} that converts each {@link Entry}'s key and value to a {@link String}.
     */
    Iterator<Entry<String, String>> iteratorAsString();

    /**
     * Returns {@code true} if a header with the {@code name} and {@code value} exists, {@code false} otherwise.
     * <p>
     * If {@code ignoreCase} is {@code true} then a case insensitive compare is done on the value.
     * @param name the name of the header to find
     * @param value the value of the header to find
     * @param ignoreCase {@code true} then a case insensitive compare is run to compare values.
     * otherwise a case sensitive compare is run to compare values.
     */
    boolean contains(CharSequence name, CharSequence value, boolean ignoreCase);
}
