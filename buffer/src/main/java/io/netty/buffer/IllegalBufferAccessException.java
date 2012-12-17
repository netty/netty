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

package io.netty.buffer;

/**
 * An {@link IllegalStateException} raised when a user attempts to access a {@link Buf} which was freed by
 * {@link Buf#free()} already.
 */
public class IllegalBufferAccessException extends IllegalStateException {

    private static final long serialVersionUID = -6734326916218551327L;

    public IllegalBufferAccessException() { }

    public IllegalBufferAccessException(String message) {
        super(message);
    }

    public IllegalBufferAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalBufferAccessException(Throwable cause) {
        super(cause);
    }
}
