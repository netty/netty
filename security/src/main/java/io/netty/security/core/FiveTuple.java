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
 * FiveTuple stores 5 elements of a network connection.
 * <p>
 * <ol>
 *     <li> Protocol (TCP/UDP) </li>
 *     <li> Source Port </li>
 *     <li> Destination Port </li>
 *     <li> Source IP Address </li>
 *     <li> Destination IP Address </li>
 * </ol>
 */
public interface FiveTuple extends Comparable<FiveTuple> {

    /**
     * Tuple {@link Protocol}
     */
    Protocol protocol();

    /**
     * Tuple Source Port
     */
    int sourcePort();

    /**
     * Tuple Destination Port
     */
    int destinationPort();

    /**
     * Tuple Source {@link Address}
     */
    Address sourceIpAddress();

    /**
     * Source Destination {@link Address}
     */
    Address destinationIpAddress();

    @Override
    int compareTo(FiveTuple o);
}
