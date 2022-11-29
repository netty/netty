/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core.standards;

import io.netty.security.core.payload.RegexPayload;
import io.netty.util.internal.ObjectUtil;

import java.util.regex.Pattern;

/**
 * {@link StandardRegexPayload} holds Payload Needle as {@link Pattern}
 * which is used to match Payload Haystack.
 */
public final class StandardRegexPayload implements RegexPayload {

    private final Pattern pattern;

    private StandardRegexPayload(Pattern pattern) {
        this.pattern = ObjectUtil.checkNotNull(pattern, "Pattern");
    }

    /**
     * Create a new {@link StandardRegexPayload} with specified {@link Pattern}
     *
     * @param pattern {@link Pattern} to use as needle
     * @return New {@link StandardRegexPayload} instance
     */
    public static StandardRegexPayload create(Pattern pattern) {
        return new StandardRegexPayload(pattern);
    }

    @Override
    public Pattern needle() {
        return pattern;
    }
}
