/*
 * Copyright 2015 The Netty Project
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
package io.netty.jni.libaio;

/**
 * In case of a failure the callback will be replaced by an instance of this class.
 * This will be created by the native layer on the rare event of a failure.
 */
public final class ErrorInfo {

    private final Object callback;
    private final int error;
    private final String message;

    /**
     * Constructor called from the native layer during polling in case of failures.
     */
    public ErrorInfo(Object callback, int error, String message) {
        this.callback = callback;
        this.error = error;
        this.message = message;
    }

    /**
     * This will contain the original callback replaced by this failure.
     */
    public Object callback() {
        return callback;
    }

    /**
     * The error that happened accordingly to errno (use man errno on linux for more information).
     */
    public int error() {
        return error;
    }

    /**
     * The textual information about the error that happened.
     */
    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "ErrorInfo{" +
               "callback=" + callback +
               ", error=" + error +
               ", message='" + message + '\'' +
               '}';
    }
}
