/*
 * Copyright 2014 The Netty Project
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
 */
@Sharable
public class RuleBasedIpFilter extends AbstractRemoteAddressFilter<InetSocketAddress> {

    private final IpFilterRule[] rules;

    /**
     * Create new Instance of {@link RuleBasedIpFilter} and filter incoming connections
     * based on their IP address and {@code rules} applied.
     */
    public RuleBasedIpFilter(IpFilterRule... rules) {
        this.rules = ObjectUtil.checkNotNull(rules, "rules");

        for (IpFilterRule rule : this.rules) {
            ObjectUtil.checkNotNull(rule, "rule");
        }
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) throws Exception {
        for (IpFilterRule rule : rules) {
            if (rule.matches(remoteAddress)) {
                return rule.ruleType() == IpFilterRuleType.ACCEPT;
            }
        }

        return true;
    }
}
