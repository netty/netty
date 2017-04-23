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

import io.netty.util.internal.SocketUtils;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Use this class to create rules for {@link RuleBasedIpFilter} that group IP addresses into subnets.
 * Supports both, IPv4 and IPv6.
 */
public final class IpSubnetFilterRule implements IpFilterRule {

    private final IpFilterRule filterRule;

    public IpSubnetFilterRule(String ipAddress, int cidrPrefix, IpFilterRuleType ruleType) {
        try {
            filterRule = selectFilterRule(SocketUtils.addressByName(ipAddress), cidrPrefix, ruleType);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("ipAddress", e);
        }
    }

    public IpSubnetFilterRule(InetAddress ipAddress, int cidrPrefix, IpFilterRuleType ruleType) {
        filterRule = selectFilterRule(ipAddress, cidrPrefix, ruleType);
    }

    private static IpFilterRule selectFilterRule(InetAddress ipAddress, int cidrPrefix, IpFilterRuleType ruleType) {
        if (ipAddress == null) {
            throw new NullPointerException("ipAddress");
        }

        if (ruleType == null) {
            throw new NullPointerException("ruleType");
        }

        if (ipAddress instanceof Inet4Address) {
            return new Ip4SubnetFilterRule((Inet4Address) ipAddress, cidrPrefix, ruleType);
        } else if (ipAddress instanceof Inet6Address) {
            return new Ip6SubnetFilterRule((Inet6Address) ipAddress, cidrPrefix, ruleType);
        } else {
            throw new IllegalArgumentException("Only IPv4 and IPv6 addresses are supported");
        }
    }

    @Override
    public boolean matches(InetSocketAddress remoteAddress) {
        return filterRule.matches(remoteAddress);
    }

    @Override
    public IpFilterRuleType ruleType() {
        return filterRule.ruleType();
    }

    private static final class Ip4SubnetFilterRule implements IpFilterRule {

        private final int networkAddress;
        private final int subnetMask;
        private final IpFilterRuleType ruleType;

        private Ip4SubnetFilterRule(Inet4Address ipAddress, int cidrPrefix, IpFilterRuleType ruleType) {
            if (cidrPrefix < 0 || cidrPrefix > 32) {
                throw new IllegalArgumentException(String.format("IPv4 requires the subnet prefix to be in range of " +
                                                                    "[0,32]. The prefix was: %d", cidrPrefix));
            }

            subnetMask = prefixToSubnetMask(cidrPrefix);
            networkAddress = ipToInt(ipAddress) & subnetMask;
            this.ruleType = ruleType;
        }

        @Override
        public boolean matches(InetSocketAddress remoteAddress) {
            final InetAddress inetAddress = remoteAddress.getAddress();
            if (inetAddress instanceof Inet4Address) {
                int ipAddress = ipToInt((Inet4Address) inetAddress);
                return (ipAddress & subnetMask) == networkAddress;
            }
            return false;
        }

        @Override
        public IpFilterRuleType ruleType() {
            return ruleType;
        }

        private static int ipToInt(Inet4Address ipAddress) {
            byte[] octets = ipAddress.getAddress();
            assert octets.length == 4;

            return (octets[0] & 0xff) << 24 |
                   (octets[1] & 0xff) << 16 |
                   (octets[2] & 0xff) << 8 |
                    octets[3] & 0xff;
        }

        private static int prefixToSubnetMask(int cidrPrefix) {
            /**
             * Perform the shift on a long and downcast it to int afterwards.
             * This is necessary to handle a cidrPrefix of zero correctly.
             * The left shift operator on an int only uses the five least
             * significant bits of the right-hand operand. Thus -1 << 32 evaluates
             * to -1 instead of 0. The left shift operator applied on a long
             * uses the six least significant bits.
             *
             * Also see https://github.com/netty/netty/issues/2767
             */
            return (int) ((-1L << 32 - cidrPrefix) & 0xffffffff);
        }
    }

    private static final class Ip6SubnetFilterRule implements IpFilterRule {

        private static final BigInteger MINUS_ONE = BigInteger.valueOf(-1);

        private final BigInteger networkAddress;
        private final BigInteger subnetMask;
        private final IpFilterRuleType ruleType;

        private Ip6SubnetFilterRule(Inet6Address ipAddress, int cidrPrefix, IpFilterRuleType ruleType) {
            if (cidrPrefix < 0 || cidrPrefix > 128) {
                throw new IllegalArgumentException(String.format("IPv6 requires the subnet prefix to be in range of " +
                                                                    "[0,128]. The prefix was: %d", cidrPrefix));
            }

            subnetMask = prefixToSubnetMask(cidrPrefix);
            networkAddress = ipToInt(ipAddress).and(subnetMask);
            this.ruleType = ruleType;
        }

        @Override
        public boolean matches(InetSocketAddress remoteAddress) {
            final InetAddress inetAddress = remoteAddress.getAddress();
            if (inetAddress instanceof Inet6Address) {
                BigInteger ipAddress = ipToInt((Inet6Address) inetAddress);
                return ipAddress.and(subnetMask).equals(networkAddress);
            }
            return false;
        }

        @Override
        public IpFilterRuleType ruleType() {
            return ruleType;
        }

        private static BigInteger ipToInt(Inet6Address ipAddress) {
            byte[] octets = ipAddress.getAddress();
            assert octets.length == 16;

            return new BigInteger(octets);
        }

        private static BigInteger prefixToSubnetMask(int cidrPrefix) {
            return MINUS_ONE.shiftLeft(128 - cidrPrefix);
        }
    }
}
