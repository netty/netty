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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides the constants for the standard STOMP header names and values and
 * commonly used utility methods that accesses an {@link StompHeadersSubframe}.
 */
public class StompHeaders {

    public static final String ACCEPT_VERSION = "accept-version";
    public static final String HOST = "host";
    public static final String LOGIN = "login";
    public static final String PASSCODE = "passcode";
    public static final String HEART_BEAT = "heart-beat";
    public static final String VERSION = "version";
    public static final String SESSION = "session";
    public static final String SERVER = "server";
    public static final String DESTINATION = "destination";
    public static final String ID = "id";
    public static final String ACK = "ack";
    public static final String TRANSACTION = "transaction";
    public static final String RECEIPT = "receipt";
    public static final String MESSAGE_ID = "message-id";
    public static final String SUBSCRIPTION = "subscription";
    public static final String RECEIPT_ID = "receipt-id";
    public static final String MESSAGE = "message";
    public static final String CONTENT_LENGTH = "content-length";
    public static final String CONTENT_TYPE = "content-type";

    private final Map<String, List<String>> headers = new HashMap<String, List<String>>();

    public boolean has(String key) {
        List<String> values = headers.get(key);
        return values != null && !values.isEmpty();
    }

    public String get(String key) {
        List<String> values = headers.get(key);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        } else {
            return null;
        }
    }

    public void add(String key, String value) {
        List<String> values = headers.get(key);
        if (values == null) {
            values = new ArrayList<String>();
            headers.put(key, values);
        }
        values.add(value);
    }

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
    public void set(String key, String value) {
        headers.put(key, Arrays.asList(value));
    }

    public List<String> getAll(String key) {
        List<String> values = headers.get(key);
        if (values != null) {
            return new ArrayList<String>(values);
        } else {
            return new ArrayList<String>();
        }
    }

    public Set<String> keySet() {
        return headers.keySet();
    }

    @Override
    public String toString() {
        return "StompHeaders{" +
            headers +
            '}';
    }

    public void set(StompHeaders headers) {
        for (String key: headers.keySet()) {
            List<String> values = headers.getAll(key);
            this.headers.put(key, values);
        }
    }
}
