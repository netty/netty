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

/**
 * Payload or Needle is the data to match against actual Payload or Haystack.
 *
 * @param <P> Payload type
 */
@FunctionalInterface
public interface Payload<P> {

    /**
     * Returns {@code null} payload needle
     */
    Payload<?> NULL_PAYLOAD = new Payload<Object>() {
        @Override
        public Object needle() {
            return null;
        }
    };

    /**
     * Returns Payload Needle
     */
    P needle();
}
