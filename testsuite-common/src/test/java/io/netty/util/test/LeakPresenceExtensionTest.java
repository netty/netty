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
package io.netty.util.test;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class LeakPresenceExtensionTest {

    @Test
    void nestedClassLifecycleIsSupported() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(OuterNestedTest.class))
                .execute();

        results.containerEvents().failed().assertThatEvents().isEmpty();
        results.testEvents().failed().assertThatEvents().isEmpty();
        results.testEvents().succeeded().assertThatEvents().hasSize(2);
    }

    @Test
    void explicitExtensionTestIsSkippedWhenLeakPresenceDetectionIsDisabled() {
        String previous = System.getProperty(LeakPresenceExtension.LEAK_PRESENCE_DETECTION_DISABLED_PROPERTY);
        System.setProperty(LeakPresenceExtension.LEAK_PRESENCE_DETECTION_DISABLED_PROPERTY, "true");
        try {
            EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(ExplicitExtensionTest.class))
                    .execute();

            results.containerEvents().failed().assertThatEvents().isEmpty();
            results.containerEvents().skipped().assertThatEvents().hasSize(1);
            results.testEvents().succeeded().assertThatEvents().isEmpty();
        } finally {
            if (previous == null) {
                System.clearProperty(LeakPresenceExtension.LEAK_PRESENCE_DETECTION_DISABLED_PROPERTY);
            } else {
                System.setProperty(LeakPresenceExtension.LEAK_PRESENCE_DETECTION_DISABLED_PROPERTY, previous);
            }
        }
    }

    @Test
    void inheritedTestClassExecutesParentAndChildTests() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(InheritedChildTest.class))
                .execute();

        results.containerEvents().failed().assertThatEvents().isEmpty();
        results.testEvents().failed().assertThatEvents().isEmpty();
        results.testEvents().succeeded().assertThatEvents().hasSize(2);
    }

    static class OuterNestedTest {
        @Test
        void outerTest() {
        }

        @Nested
        class InnerTest {
            @Test
            void innerTest() {
            }
        }
    }

    static class InheritedParentTest {
        @Test
        void parentTest() {
        }
    }

    static class InheritedChildTest extends InheritedParentTest {
        @Test
        void childTest() {
        }
    }

    @ExtendWith(LeakPresenceExtension.class)
    static class ExplicitExtensionTest {
        @Test
        void test() {
        }
    }
}
