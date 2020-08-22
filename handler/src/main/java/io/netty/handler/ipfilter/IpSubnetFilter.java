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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

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

    private final List<IpSubnetFilterRule> rules;

    public IpSubnetFilter(IpSubnetFilterRule... rules) {
        this(Arrays.asList(ObjectUtil.checkNotNull(rules, "rules")));
    }

    public IpSubnetFilter(List<IpSubnetFilterRule> rules) {
        this.rules = ObjectUtil.checkNotNull(rules, "rules");

        // Iterate over rules and check for `null` rule.
        for (IpSubnetFilterRule ipSubnetFilterRule : this.rules) {
            ObjectUtil.checkNotNull(ipSubnetFilterRule, "rule");
        }

        sortAndFilter();
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        int indexOf = Collections.binarySearch(this.rules, remoteAddress, IpSubnetFilterRuleComparator.INSTANCE);
        if (indexOf >= 0) {
            return this.rules.get(indexOf).ruleType() == IpFilterRuleType.ACCEPT;
        }
        return true;
    }

    /**
     * <ol>
     *     <li> Sort the list </li>
     *     <li> Remove over-lapping CIDR </li>
     *     <li> Sort the list again </li>
     * </ol>
     */
    private void sortAndFilter() {
        Collections.sort(this.rules);
        Iterator<IpSubnetFilterRule> iterator = rules.iterator();
        List<IpSubnetFilterRule> toRemove = new ArrayList<IpSubnetFilterRule>();

        try {
            IpSubnetFilterRule parentRule = null;
            while (iterator.hasNext()) {

                // If parentRule is null, take first element out of Iterator.
                if (parentRule == null) {
                    parentRule = iterator.next();
                }

                // Take one more element out of Iterator, this will be childRule.
                IpSubnetFilterRule childRule = iterator.next();

                // If parentRule matches childRule, schedule that Rule for deletion.
                if (parentRule.matches(new InetSocketAddress(childRule.getIpAddress(), 1))) {
                    toRemove.add(childRule);
                } else {
                    // If parentRule does not matches childRule, this childRule will become new parentRule
                    // and we'll do the same again
                    parentRule = childRule;
                }
            }
        } catch (NoSuchElementException ex) {
            // Ignore
        }

        rules.removeAll(toRemove);
        toRemove.clear();
        Collections.sort(rules);
    }
}
