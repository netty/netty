/*
 * Copyright 2025 The Netty Project
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

import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import org.junit.jupiter.api.condition.DisabledIf;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Disable a test when slow leak detection is enabled.
 */
@DisabledIf("io.netty.util.test.DisabledForSlowLeakDetection$Helper#slowLeakDetection")
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface DisabledForSlowLeakDetection {
    final class Helper {
        private static final ResourceLeakDetector<Helper> DETECTOR =
                ResourceLeakDetectorFactory.instance().newResourceLeakDetector(Helper.class);

        static boolean slowLeakDetection() {
            return DETECTOR.isRecordEnabled();
        }
    }
}
