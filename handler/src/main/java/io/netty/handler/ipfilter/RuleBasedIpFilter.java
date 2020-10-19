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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ObjectUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * This class allows one to filter new {@link Channel}s based on the
 * {@link IpFilterRule}s passed to its constructor. If no rules are provided, all connections
 * will be accepted.
 * </p>
 *
 * <p>
 * If you would like to explicitly take action on rejected {@link Channel}s, you should override
 * {@link AbstractRemoteAddressFilter#channelRejected(ChannelHandlerContext, SocketAddress)}.
 * </p>
 *
 * <p> Consider using {@link IpSubnetFilter} for better performance while not as
 * general purpose as this filter. </p>
 */
@Sharable
public class RuleBasedIpFilter extends AbstractRemoteAddressFilter<InetSocketAddress> {

    private final boolean acceptIfNotFound;
    private final List<IpFilterRule> rules;

    /**
     * <p> Create new Instance of {@link RuleBasedIpFilter} and filter incoming connections
     * based on their IP address and {@code rules} applied. </p>
     *
     * <p> {@code acceptIfNotFound} is set to {@code true}. </p>
     *
     * @param rules An array of {@link IpFilterRule} containing all rules.
     */
    public RuleBasedIpFilter(IpFilterRule... rules) {
        this(true, rules);
    }

    /**
     * Create new Instance of {@link RuleBasedIpFilter} and filter incoming connections
     * based on their IP address and {@code rules} applied.
     *
     * @param acceptIfNotFound If {@code true} then accept connection from IP Address if it
     *                         doesn't match any rule.
     * @param rules            An array of {@link IpFilterRule} containing all rules.
     */
    public RuleBasedIpFilter(boolean acceptIfNotFound, IpFilterRule... rules) {
        ObjectUtil.checkNotNull(rules, "rules");

        this.acceptIfNotFound = acceptIfNotFound;
        this.rules = new ArrayList<IpFilterRule>(rules.length);

        for (IpFilterRule rule : rules) {
            if (rule != null) {
                this.rules.add(rule);
            }
        }
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) throws Exception {
        for (IpFilterRule rule : rules) {
            if (rule.matches(remoteAddress)) {
                return rule.ruleType() == IpFilterRuleType.ACCEPT;
            }
        }

        return acceptIfNotFound;
    }
}
