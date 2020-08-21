/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.ipfilter;

import java.net.InetSocketAddress;
import java.util.Comparator;

final class IpSubnetFilterRuleComparator implements Comparator<Object> {

    static final IpSubnetFilterRuleComparator INSTANCE = new IpSubnetFilterRuleComparator();

    private IpSubnetFilterRuleComparator() {
        // Prevent outside initialization
    }

    @Override
    public int compare(Object o1, Object o2) {
        /*
         * We'll try to use `matches` method, if IP address successfully matches then
         * return 0 because we found the result.
         *
         * If `matches` returns false, we'll compare the IP address (as Integer) with
         * `networkAddress` to find the difference.
         */
        if (((IpSubnetFilterRule) o1).matches((InetSocketAddress) o2)) {
            return 0;
        } else {
            return ((IpSubnetFilterRule) o1).compareTo((InetSocketAddress) o2);
        }
    }
}
