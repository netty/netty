/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.ssl;

import javax.net.ssl.SSLException;

/**
 * {@link SSLException} which signals that the exception was caused by an {@link javax.net.ssl.SSLEngine} which was
 * closed already.
 */
public final class SslClosedEngineException extends SSLException {

    private static final long serialVersionUID = -5204207600474401904L;

    public SslClosedEngineException(String reason) {
        super(reason);
    }
}
