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
package io.netty.resolver.dns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class ResolvConfTest {
    @Test
    @DisabledOnOs({OS.WINDOWS})
    public void readSystem() {
        assertThat(ResolvConf.system().getNameservers().size(), is(greaterThan(0)));
    }

    @ParameterizedTest
    @MethodSource
    public void scenarios(String resolvConf, List<String> nameservers) throws Exception {
        assertIterableEquals(
                nameservers.stream().map(new Function<String, InetSocketAddress>() {
                    @Override
                    public InetSocketAddress apply(String n) {
                        return new InetSocketAddress(n, 53);
                    }
                }).collect(Collectors.toList()),
                ResolvConf.fromReader(new BufferedReader(new StringReader(resolvConf))).getNameservers());
    }

    static List<Arguments> scenarios() {
        return Arrays.asList(
                arguments("", emptyList()),
                arguments(
                        "# some comment\n"
                                + "# nameserver hello\n"
                                + "\n"
                                + "nameserver 1.2.3.4\n"
                                + "nameserver 127.1.2.3",
                        Arrays.asList("1.2.3.4", "127.1.2.3")),
                arguments(
                        "# some comment\n"
                                + "# nameserver hello\n"
                                + "\n"
                                + "nameserver 1.2.3.4\n"
                                + "nameserver 127.1.2.3",
                        Arrays.asList("1.2.3.4", "127.1.2.3")),
                arguments(
                        "# some comment\n"
                                + "nameserver 1.2.3.4 # comment\n"
                                + "nameserver 127.1.2.3",
                        Arrays.asList("1.2.3.4", "127.1.2.3")),
                arguments(
                        "# some comment\n" + "nameserver 0:0:0:0:0:0:0:1\n" + "nameserver 127.0.0.1",
                        Arrays.asList("0:0:0:0:0:0:0:1", "127.0.0.1")),
                arguments(
                        "# options and search are ignored\n"
                                + "nameserver 127.0.0.53\n"
                                + "options edns0 trust-ad"
                                + "search netty.io projectnessie.org",
                        Arrays.asList("127.0.0.53")));
    }
}
