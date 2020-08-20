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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ObjectUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class allows one to filter new {@link Channel}s based on the
 * {@link IpSubnetFilter}s passed to its constructor. If no rules are provided, all connections
 * will be accepted.
 * <p>
 * If you would like to explicitly take action on rejected {@link Channel}s, you should override
 * {@link #channelRejected(ChannelHandlerContext, SocketAddress)}.
 */
@Sharable
public class IpSubnetFilter extends AbstractRemoteAddressFilter<InetSocketAddress> {

    private final List<IpSubnetFilterRule> rules = new ArrayList<IpSubnetFilterRule>();

    public IpSubnetFilter(IpSubnetFilterRule... rules) {
        ObjectUtil.checkNotNull(rules, "rules");

        // Iterate over rules and check for `null` and add them to List
        for (IpSubnetFilterRule ipSubnetFilterRule : rules) {
            ObjectUtil.checkNotNull(ipSubnetFilterRule, "rule");
            this.rules.add(ipSubnetFilterRule);
        }

        Collections.sort(this.rules, new IpSubnetFilterRuleComparator());
    }

    public IpSubnetFilter(List<IpSubnetFilterRule> rules) {
        ObjectUtil.checkNotNull(rules, "rules");

        // Iterate over rules and check for `null` and add them to List
        for (IpSubnetFilterRule ipSubnetFilterRule : rules) {
            ObjectUtil.checkNotNull(ipSubnetFilterRule, "rule");
            this.rules.add(ipSubnetFilterRule);
        }

        Collections.sort(this.rules, new IpSubnetFilterRuleComparator());
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        int indexOf = Collections.binarySearch(this.rules, remoteAddress, new IpSubnetFilterRuleComparator());
        if (indexOf >= 0) {
            return this.rules.get(indexOf).ruleType() == IpFilterRuleType.ACCEPT;
        }
        return true;
    }
}
