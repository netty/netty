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
package io.netty.incubator.codec.quic;

import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.Timeout;

public abstract class AbstractQuicTest {

    private static final int TEST_GLOBAL_TIMEOUT_VALUE = Integer.getInteger(
            "io.netty.incubator.codec.quic.defaultTestTimeout", 10);

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(TEST_GLOBAL_TIMEOUT_VALUE);

    @BeforeClass
    public static void assumeTrue() {
       Assume.assumeTrue(Quic.isAvailable());
    }
}
