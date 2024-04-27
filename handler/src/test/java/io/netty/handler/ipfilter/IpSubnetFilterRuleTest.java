/*
 * Copyright 2024 The Netty Project
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IpSubnetFilterRuleTest {

    @Test
    void createInvalidIpCidrRule() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                new IpSubnetFilterRule("192.168.0.0", IpFilterRuleType.ACCEPT);
            }
        });
    }

    @Test
    void createValidIpCidrRule() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                new IpSubnetFilterRule("192.168.0.0/24", IpFilterRuleType.ACCEPT);
            }
        });
    }

    @Test
    void validateIpAddressFromIpCidrString() {
        IpSubnetFilterRule rule = new IpSubnetFilterRule("10.10.0.0/16", IpFilterRuleType.ACCEPT);
        assertThat(rule.getIpAddress()).isEqualTo("10.10.0.0");
    }
}
