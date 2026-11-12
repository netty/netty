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
package io.netty.util.internal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveCalculatorTest {

    @ParameterizedTest
    @CsvSource({ "81, 95", "81, 81", "513, 1023", "2049, 4095", "80, 80" })
    void growthStaysWithinBounds(int minimum, int maximum) {
        AdaptiveCalculator calculator = new AdaptiveCalculator(minimum, maximum, maximum);
        assertThat(calculator.nextSize()).isBetween(minimum, maximum);
        for (int i = 0; i < 10; i++) {
            calculator.record(calculator.nextSize());
            assertThat(calculator.nextSize()).isBetween(minimum, maximum);
        }
    }

    @ParameterizedTest
    @CsvSource({ "81, 95", "81, 81", "513, 1023", "2049, 4095", "80, 80" })
    void shrinkingStaysWithinBounds(int minimum, int maximum) {
        AdaptiveCalculator calculator = new AdaptiveCalculator(minimum, maximum, maximum);
        assertThat(calculator.nextSize()).isBetween(minimum, maximum);
        for (int i = 0; i < 10; i++) {
            calculator.record(0);
            assertThat(calculator.nextSize()).isBetween(minimum, maximum);
        }
    }
}
