/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.timeout;

/**
 * A {@link TimeoutException} raised by {@link ReadTimeoutHandler} when no data
 * was read within a certain period of time.
 */
public final class ReadTimeoutException extends TimeoutException {

    private static final long serialVersionUID = 169287984113283421L;

    public static final ReadTimeoutException INSTANCE = new ReadTimeoutException(true);

    public ReadTimeoutException() { }

    public ReadTimeoutException(String message) {
        super(message, false);
    }

    private ReadTimeoutException(boolean shared) {
        super(null, shared);
    }
}
