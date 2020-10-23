/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.ipfilter;

import java.net.InetSocketAddress;

/**
 * Implement this interface to create new rules.
 */
public interface IpFilterRule {
    /**
     * @return This method should return true if remoteAddress is valid according to your criteria. False otherwise.
     */
    boolean matches(InetSocketAddress remoteAddress);

    /**
     * @return This method should return {@link IpFilterRuleType#ACCEPT} if all
     * {@link IpFilterRule#matches(InetSocketAddress)} for which {@link #matches(InetSocketAddress)}
     * returns true should the accepted. If you want to exclude all of those IP addresses then
     * {@link IpFilterRuleType#REJECT} should be returned.
     */
    IpFilterRuleType ruleType();
}
