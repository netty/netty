/*
 * Copyright 2019 The Netty Project
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
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;

public class RuleBasedIpFilterTest {
    @Test
    public void testMultiRulesContainNull() throws Exception {
        final String rejectIp = "192.168.57.1";
        IpFilterRule rejectRule = new IpFilterRule() {
            @Override
            public boolean matches(InetSocketAddress remoteAddress) {
                return rejectIp.equals(remoteAddress.getHostName());
            }

            @Override
            public IpFilterRuleType ruleType() {
                return IpFilterRuleType.REJECT;
            }
        };
        IpFilterRule nullRule = null;
        RuleBasedIpFilter ruleBasedIpFilter = new RuleBasedIpFilter(nullRule, rejectRule);
        boolean accept = ruleBasedIpFilter.accept(null, SocketUtils.socketAddress(rejectIp, 1234));
        Assert.assertFalse("should reject", accept);
    }

}
