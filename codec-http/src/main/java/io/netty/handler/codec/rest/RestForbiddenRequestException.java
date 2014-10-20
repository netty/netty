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
package io.netty.handler.codec.rest;

/**
 * Raised when a request is forbidden (business exception, not technical) to raised a FORBIDDEN HTTP status
 */
public class RestForbiddenRequestException extends Exception {
    private static final long serialVersionUID = -6908529000590160305L;

    public RestForbiddenRequestException() {
    }

    public RestForbiddenRequestException(String message) {
        super(message);
    }

    public RestForbiddenRequestException(Throwable cause) {
        super(cause);
    }

    public RestForbiddenRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public RestForbiddenRequestException(String message, Throwable cause,
            boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
