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

/**
 * <p>
 * This class allows one to filter new {@link Channel}s based on the
 * {@link IpSubnetFilter}s passed to its constructor. If no rules are provided, all connections
 * will be accepted.
 * </p>
 * <p>
 * If you would like to explicitly take action on rejected {@link Channel}s, you should override
 * {@link #channelRejected(ChannelHandlerContext, SocketAddress)}. </p>
 * <p> This filter uses Binary Search for faster filtering so it's a good practice to remove
 * overlapping subnet rules and also entries should be arranged in incremental order.</p>
 */
@Sharable
public class IpSubnetFilter extends AbstractRemoteAddressFilter<InetSocketAddress> {

    private final List<IpSubnetFilterRule> rules;
    private final boolean acceptIfNotFound;
    private final IpFilterRuleType ipFilterRuleType;

    /**
     * <p> Create new {@link IpSubnetFilter} Instance with specified {@link IpSubnetFilterRule} as array. </p>
     * <p> {@code acceptIfNotFound} is set to {@code true} </p>
     *
     * @param rules {@link IpSubnetFilterRule} as array
     */
    public IpSubnetFilter(IpSubnetFilterRule... rules) {
        this(true, Arrays.asList(ObjectUtil.checkNotNull(rules, "rules")));
    }

    /**
     * <p> Create new {@link IpSubnetFilter} Instance with specified {@link IpSubnetFilterRule} as array
     * and specify if we'll accept a connection if we don't find it in the rule(s). </p>
     *
     * @param acceptIfNotFound {@code true} if we'll accept connection if not found in rule(s).
     * @param rules            {@link IpSubnetFilterRule} as array
     */
    public IpSubnetFilter(boolean acceptIfNotFound, IpSubnetFilterRule... rules) {
        this(acceptIfNotFound, Arrays.asList(ObjectUtil.checkNotNull(rules, "rules")));
    }

    /**
     * <p> Create new {@link IpSubnetFilter} Instance with specified {@link IpSubnetFilterRule} as {@link List}. </p>
     * <p> {@code acceptIfNotFound} is set to {@code true} </p>
     *
     * @param rules {@link IpSubnetFilterRule} as {@link List}
     */
    public IpSubnetFilter(List<IpSubnetFilterRule> rules) {
        this(true, rules);
    }

    /**
     * <p> Create new {@link IpSubnetFilter} Instance with specified {@link IpSubnetFilterRule} as {@link List}
     * and specify if we'll accept a connection if we don't find it in the rule(s). </p>
     *
     * @param acceptIfNotFound {@code true} if we'll accept connection if not found in rule(s).
     * @param rules            {@link IpSubnetFilterRule} as {@link List}
     */
    public IpSubnetFilter(boolean acceptIfNotFound, List<IpSubnetFilterRule> rules) {
        this.rules = ObjectUtil.checkNotNull(rules, "rules");
        this.acceptIfNotFound = acceptIfNotFound;

        int numAccept = 0;
        int numReject = 0;

        // Iterate over rules and check for `null` rule.
        for (IpSubnetFilterRule ipSubnetFilterRule : this.rules) {
            ObjectUtil.checkNotNull(ipSubnetFilterRule, "rule");
            if (ipSubnetFilterRule.ruleType() == IpFilterRuleType.ACCEPT) {
                numAccept++;
            } else {
                numReject++;
            }
        }

        /*
         * If Number of accept rules are 0 and Number of reject rules is more than 0,
         * then all rules are of "REJECT" type.
         *
         * In this case, we'll set `ipFilterRuleType` to `IpFilterRuleType.REJECT`
         *
         * If Number of accept rules are more than 0 and number of reject rules are 0,
         * then all rules are of "ACCEPT" type.
         *
         */
        if (numAccept == 0 && numReject > 0) {
            ipFilterRuleType = IpFilterRuleType.REJECT;
        } else if (numAccept > 0 && numReject == 0) {
            ipFilterRuleType = IpFilterRuleType.ACCEPT;
        } else {
            ipFilterRuleType = null;
        }

        sortAndFilter();
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        int indexOf = Collections.binarySearch(this.rules, remoteAddress, IpSubnetFilterRuleComparator.INSTANCE);
        if (indexOf >= 0) {
            if (ipFilterRuleType == null) {
                return this.rules.get(indexOf).ruleType() == IpFilterRuleType.ACCEPT;
            } else {
                return this.ipFilterRuleType == IpFilterRuleType.ACCEPT;
            }
        }
        return acceptIfNotFound;
    }

    /**
     * <ol>
     *     <li> Sort the list </li>
     *     <li> Remove over-lapping subnet </li>
     *     <li> Sort the list again </li>
     * </ol>
     */
    private void sortAndFilter() {
        Collections.sort(this.rules);
        Iterator<IpSubnetFilterRule> iterator = rules.iterator();
        List<IpSubnetFilterRule> toRemove = new ArrayList<IpSubnetFilterRule>();

        IpSubnetFilterRule parentRule = null;
        while (iterator.hasNext()) {

            // If parentRule is null, take first element out of Iterator.
            if (parentRule == null) {
                parentRule = iterator.next();
            }

            // If we don't have any more rule, we're done now.
            if (!iterator.hasNext()) {
                break;
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

        rules.removeAll(toRemove);
        toRemove.clear();
        Collections.sort(rules); // Re-sort just to be sure
    }
}
