/*
 * Copyright 2021 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.channel.epoll.Epoll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class CombinationOfEpollAndIOUringTest {

    @BeforeAll
    public static void loadJNI() {
        // Epoll must be usable.
        Epoll.ensureAvailability();
        assumeTrue(IOUring.isAvailable());
    }

    @Test
    public void testEpollAndIOUringCanBothBeLoaded() {
        Epoll.ensureAvailability();
        IOUring.ensureAvailability();
    }
}
