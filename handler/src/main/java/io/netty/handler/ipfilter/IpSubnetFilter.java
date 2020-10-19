/*
 * Copyright 2020 The Netty Project
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

import java.net.Inet4Address;
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
 * will be accepted since {@code acceptIfNotFound} is {@code true} by default.
 * </p>
 *
 * <p>
 * If you would like to explicitly take action on rejected {@link Channel}s, you should override
 * {@link AbstractRemoteAddressFilter#channelRejected(ChannelHandlerContext, SocketAddress)}.
 * </p>
 *
 * <p>
 *     Few Points to keep in mind:
 *     <ol>
 *         <li> Since {@link IpSubnetFilter} uses Binary search algorithm, it's a good
 *         idea to insert IP addresses in incremental order. </li>
 *         <li> Remove any over-lapping CIDR.  </li>
 *     </ol>
 * </p>
 *
 */
@Sharable
public class IpSubnetFilter extends AbstractRemoteAddressFilter<InetSocketAddress> {

    private final boolean acceptIfNotFound;
    private final List<IpSubnetFilterRule> ipv4Rules;
    private final List<IpSubnetFilterRule> ipv6Rules;
    private final IpFilterRuleType ipFilterRuleTypeIPv4;
    private final IpFilterRuleType ipFilterRuleTypeIPv6;

    /**
     * <p> Create new {@link IpSubnetFilter} Instance with specified {@link IpSubnetFilterRule} as array. </p>
     * <p> {@code acceptIfNotFound} is set to {@code true}. </p>
     *
     * @param rules {@link IpSubnetFilterRule} as an array
     */
    public IpSubnetFilter(IpSubnetFilterRule... rules) {
        this(true, Arrays.asList(ObjectUtil.checkNotNull(rules, "rules")));
    }

    /**
     * <p> Create new {@link IpSubnetFilter} Instance with specified {@link IpSubnetFilterRule} as array
     * and specify if we'll accept a connection if we don't find it in the rule(s). </p>
     *
     * @param acceptIfNotFound {@code true} if we'll accept connection if not found in rule(s).
     * @param rules            {@link IpSubnetFilterRule} as an array
     */
    public IpSubnetFilter(boolean acceptIfNotFound, IpSubnetFilterRule... rules) {
        this(acceptIfNotFound, Arrays.asList(ObjectUtil.checkNotNull(rules, "rules")));
    }

    /**
     * <p> Create new {@link IpSubnetFilter} Instance with specified {@link IpSubnetFilterRule} as {@link List}. </p>
     * <p> {@code acceptIfNotFound} is set to {@code true}. </p>
     *
     * @param rules {@link IpSubnetFilterRule} as a {@link List}
     */
    public IpSubnetFilter(List<IpSubnetFilterRule> rules) {
        this(true, rules);
    }

    /**
     * <p> Create new {@link IpSubnetFilter} Instance with specified {@link IpSubnetFilterRule} as {@link List}
     * and specify if we'll accept a connection if we don't find it in the rule(s). </p>
     *
     * @param acceptIfNotFound {@code true} if we'll accept connection if not found in rule(s).
     * @param rules            {@link IpSubnetFilterRule} as a {@link List}
     */
    public IpSubnetFilter(boolean acceptIfNotFound, List<IpSubnetFilterRule> rules) {
        ObjectUtil.checkNotNull(rules, "rules");
        this.acceptIfNotFound = acceptIfNotFound;

        int numAcceptIPv4 = 0;
        int numRejectIPv4 = 0;
        int numAcceptIPv6 = 0;
        int numRejectIPv6 = 0;

        List<IpSubnetFilterRule> unsortedIPv4Rules = new ArrayList<IpSubnetFilterRule>();
        List<IpSubnetFilterRule> unsortedIPv6Rules = new ArrayList<IpSubnetFilterRule>();

        // Iterate over rules and check for `null` rule.
        for (IpSubnetFilterRule ipSubnetFilterRule : rules) {
            ObjectUtil.checkNotNull(ipSubnetFilterRule, "rule");

            if (ipSubnetFilterRule.getFilterRule() instanceof IpSubnetFilterRule.Ip4SubnetFilterRule) {
                unsortedIPv4Rules.add(ipSubnetFilterRule);

                if (ipSubnetFilterRule.ruleType() == IpFilterRuleType.ACCEPT) {
                    numAcceptIPv4++;
                } else {
                    numRejectIPv4++;
                }
            } else {
                unsortedIPv6Rules.add(ipSubnetFilterRule);

                if (ipSubnetFilterRule.ruleType() == IpFilterRuleType.ACCEPT) {
                    numAcceptIPv6++;
                } else {
                    numRejectIPv6++;
                }
            }
        }

        /*
         * If Number of ACCEPT rule is 0 and number of REJECT rules is more than 0,
         * then all rules are of "REJECT" type.
         *
         * In this case, we'll set `ipFilterRuleTypeIPv4` to `IpFilterRuleType.REJECT`.
         *
         * If Number of ACCEPT rules are more than 0 and number of REJECT rules is 0,
         * then all rules are of "ACCEPT" type.
         *
         * In this case, we'll set `ipFilterRuleTypeIPv4` to `IpFilterRuleType.ACCEPT`.
         */
        if (numAcceptIPv4 == 0 && numRejectIPv4 > 0) {
            ipFilterRuleTypeIPv4 = IpFilterRuleType.REJECT;
        } else if (numAcceptIPv4 > 0 && numRejectIPv4 == 0) {
            ipFilterRuleTypeIPv4 = IpFilterRuleType.ACCEPT;
        } else {
            ipFilterRuleTypeIPv4 = null;
        }

        if (numAcceptIPv6 == 0 && numRejectIPv6 > 0) {
            ipFilterRuleTypeIPv6 = IpFilterRuleType.REJECT;
        } else if (numAcceptIPv6 > 0 && numRejectIPv6 == 0) {
            ipFilterRuleTypeIPv6 = IpFilterRuleType.ACCEPT;
        } else {
            ipFilterRuleTypeIPv6 = null;
        }

        this.ipv4Rules = sortAndFilter(unsortedIPv4Rules);
        this.ipv6Rules = sortAndFilter(unsortedIPv6Rules);
    }

    @Override
    protected boolean accept(ChannelHandlerContext ctx, InetSocketAddress remoteAddress) {
        if (remoteAddress.getAddress() instanceof Inet4Address) {
            int indexOf = Collections.binarySearch(ipv4Rules, remoteAddress, IpSubnetFilterRuleComparator.INSTANCE);
            if (indexOf >= 0) {
                if (ipFilterRuleTypeIPv4 == null) {
                    return ipv4Rules.get(indexOf).ruleType() == IpFilterRuleType.ACCEPT;
                } else {
                    return ipFilterRuleTypeIPv4 == IpFilterRuleType.ACCEPT;
                }
            }
        } else {
            int indexOf = Collections.binarySearch(ipv6Rules, remoteAddress, IpSubnetFilterRuleComparator.INSTANCE);
            if (indexOf >= 0) {
                if (ipFilterRuleTypeIPv6 == null) {
                    return ipv6Rules.get(indexOf).ruleType() == IpFilterRuleType.ACCEPT;
                } else {
                    return ipFilterRuleTypeIPv6 == IpFilterRuleType.ACCEPT;
                }
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
    @SuppressWarnings("ConstantConditions")
    private static List<IpSubnetFilterRule> sortAndFilter(List<IpSubnetFilterRule> rules) {
        Collections.sort(rules);
        Iterator<IpSubnetFilterRule> iterator = rules.iterator();
        List<IpSubnetFilterRule> toKeep = new ArrayList<IpSubnetFilterRule>();

        IpSubnetFilterRule parentRule = iterator.hasNext() ? iterator.next() : null;
        if (parentRule != null) {
            toKeep.add(parentRule);
        }

        while (iterator.hasNext()) {

            // Grab a potential child rule.
            IpSubnetFilterRule childRule = iterator.next();

            // If parentRule matches childRule, then there's no need to keep the child rule.
            // Otherwise, the rules are distinct and we need both.
            if (!parentRule.matches(new InetSocketAddress(childRule.getIpAddress(), 1))) {
                toKeep.add(childRule);
                // Then we'll keep the child rule around as the parent for the next round.
                parentRule = childRule;
            }
        }

        return toKeep;
    }
}
