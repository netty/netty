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
package io.netty.handler.codec;

/**
 * Thrown if an unsupported message is received by an codec.
 */
public class UnsupportedMessageTypeException extends CodecException {

    private static final long serialVersionUID = 2799598826487038726L;

    public UnsupportedMessageTypeException(
            Object message, Class<?>... expectedTypes) {
        super(message(
                message == null? "null" : message.getClass().getName(), expectedTypes));
    }

    public UnsupportedMessageTypeException() { }

    public UnsupportedMessageTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedMessageTypeException(String s) {
        super(s);
    }

    public UnsupportedMessageTypeException(Throwable cause) {
        super(cause);
    }

    private static String message(
            String actualType, Class<?>... expectedTypes) {
        StringBuilder buf = new StringBuilder(actualType);

        if (expectedTypes != null && expectedTypes.length > 0) {
            buf.append(" (expected: ").append(expectedTypes[0].getName());
            for (int i = 1; i < expectedTypes.length; i ++) {
                Class<?> t = expectedTypes[i];
                if (t == null) {
                    break;
                }
                buf.append(", ").append(t.getName());
            }
            buf.append(')');
        }

        return buf.toString();
    }
}
