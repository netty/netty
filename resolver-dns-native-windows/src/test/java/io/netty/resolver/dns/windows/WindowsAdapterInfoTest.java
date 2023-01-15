/*
 * Copyright 2022 The Netty Project
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
package io.netty.resolver.dns.windows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.WINDOWS)
public class WindowsAdapterInfoTest {
    @Test
    void loads() {
        System.out.println("Loading Exception (if any): " + WindowsAdapterInfo.unavailabilityCause());

        assertThat(WindowsAdapterInfo.isAvailable()).isTrue();
        assertThat(WindowsAdapterInfo.adapters()).isNotNull();

        NetworkAdapter[] adapters = WindowsAdapterInfo.adapters();

        for (NetworkAdapter adapter : adapters) {
            System.out.println("=== Search Domains ===");
            System.out.println(Arrays.toString(adapter.getSearchDomains()));
            System.out.println("=== Nameservers ===");
            System.out.println(Arrays.toString(adapter.getNameservers()));
        }
    }
}
