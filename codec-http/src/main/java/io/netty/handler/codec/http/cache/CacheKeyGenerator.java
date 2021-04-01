/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.http.cache;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public class CacheKeyGenerator {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CacheKeyGenerator.class);

    public String generateKey(HttpRequest request) {
        final String host = request.headers().get(HttpHeaderNames.HOST);
        if (StringUtil.isNullOrEmpty(host)) {
            logger.debug("Can't generate cache key, the request has no meaningful HOST header.");
            return null;
        }

        final StringBuilder stringBuilder = new StringBuilder(host);
        stringBuilder.append(request.uri());

        return stringBuilder.toString();
    }
}
