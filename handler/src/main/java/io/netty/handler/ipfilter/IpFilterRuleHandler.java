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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;

/**
 * This class allows one to filter new {@link Channel}s based on the
 * {@link IpFilterRule}s passed to its constructor. If no rules are provided, all connections
 * will be accepted.
 *
 * If you would like to explicitly take action on rejected {@link Channel}s, you should override
 * {@link #rejected(ChannelHandlerContext, InetSocketAddress)}.
 */
@Sharable
public class IpFilterRuleHandler extends AbstractIpFilterHandler {
    private final IpFilterRule[] rules;

    public IpFilterRuleHandler(IpFilterRule... rules) {
        if (rules == null) {
            throw new NullPointerException("rules");
        }

        if (rules.length == 0) {
            throw new IllegalArgumentException("You have to provide at least one rule.");
        }

        this.rules = rules;
    }

    @Override
    protected boolean accept(InetSocketAddress ipAndPort) throws Exception {
        for (IpFilterRule rule : rules) {
            if (rule.matches(ipAndPort)) {
                return rule.ruleType() == IpFilterRuleType.ALLOW;
            }
        }
        return true;
    }

    @Override
    protected ChannelFuture rejected(ChannelHandlerContext ctx, InetSocketAddress ipAndPort) {
        return null;
    }
}
