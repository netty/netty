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
package io.netty.util.concurrent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemTickerNativeImageConfigurationTest {

    @Test
    void systemTickerIsInitializedAtRuntime() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "META-INF/native-image/io.netty/netty-common/native-image.properties")) {
            assertNotNull(input);
            properties.load(input);
        }

        String args = properties.getProperty("Args");
        assertNotNull(args);
        assertTrue(Arrays.stream(args.split("\\s+"))
                .filter(argument -> argument.startsWith("--initialize-at-run-time="))
                .anyMatch(argument -> Arrays.asList(argument.substring("--initialize-at-run-time=".length())
                        .split(",")).contains(SystemTicker.class.getName())));
    }
}
