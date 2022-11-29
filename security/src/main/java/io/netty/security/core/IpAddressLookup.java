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
package io.netty.security.core;

/**
 * This interface handles lookup of {@link Address}.
 */
public interface IpAddressLookup {

    /**
     * Perform lookup for an {@link Address} in database
     *
     * @return Index at which {@link Address} is located inside
     * {@link IpAddresses}. Whole number is returned when lookup
     * was successful else negative number if lookup was not successful.
     */
    int lookup(Address address);

    /**
     * Perform lookup for an {@link Address} in database
     *
     * @return {@code true} if {@link Address} was found in the
     * database or else {@code false}
     */
    boolean lookupAddress(Address address);
}
