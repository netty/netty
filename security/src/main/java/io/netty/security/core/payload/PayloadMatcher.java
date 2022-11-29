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
package io.netty.security.core.payload;

@FunctionalInterface
public interface PayloadMatcher<N, H> {

    /**
     * Match any payload
     */
    PayloadMatcher<Object, Object> ANY_PAYLOAD = new PayloadMatcher<Object, Object>() {
        @Override
        public boolean validate(Object needle, Object haystack) {
            return true;
        }
    };

    /**
     * Validate a payload
     *
     * @param needle   Needle
     * @param haystack Haystack
     * @return {@link Boolean#TRUE} if payload matches else {@link Boolean#FALSE}
     */
    boolean validate(N needle, H haystack);
}
