/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.sun.nio.sctp;

public class UnsupportedOperatingSystemException extends RuntimeException {

    private static final long serialVersionUID = -221782446524784377L;

    public static void raise() {
        throw new UnsupportedOperatingSystemException();
    }

    public UnsupportedOperatingSystemException() {
    }

    public UnsupportedOperatingSystemException(String message) {
        super(message);
    }

    public UnsupportedOperatingSystemException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsupportedOperatingSystemException(Throwable cause) {
        super(cause);
    }
}
