/*
 * Copyright 2026 The Netty Project
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
package io.netty.util;

import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(RunInFastThreadLocalThreadExtension.class)
public class RunInFastThreadLocalThreadExtensionTest {
    @Test
    void normalTest() {
        assertTrue(FastThreadLocalThread.currentThreadHasFastThreadLocal());
    }

    @RepeatedTest(1)
    void repeatedTest() {
        assertTrue(FastThreadLocalThread.currentThreadHasFastThreadLocal());
    }

    @ParameterizedTest
    @ValueSource(ints = 1)
    void parameterizedTest(int ignoreParameter) {
        assertTrue(FastThreadLocalThread.currentThreadHasFastThreadLocal());
    }
}
