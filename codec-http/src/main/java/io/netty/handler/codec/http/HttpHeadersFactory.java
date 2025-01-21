/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.http;

/**
 * An interface for creating {@link HttpHeaders} instances.
 * <p>
 * The default implementation is {@link DefaultHttpHeadersFactory},
 * and the default instance is {@link DefaultHttpHeadersFactory#headersFactory()}.
 */
public interface HttpHeadersFactory {
    /**
     * Create a new {@link HttpHeaders} instance.
     */
    HttpHeaders newHeaders();

    /**
     * Create a new {@link HttpHeaders} instance, but sized to be as small an object as possible.
     */
    HttpHeaders newEmptyHeaders();
}
