/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ChannelOptionTest {

    @Test
    public void testExists() {
        String name = "test";
        assertFalse(ChannelOption.exists(name));
        ChannelOption<String> option = ChannelOption.valueOf(name);

        assertTrue(ChannelOption.exists(name));
        assertNotNull(option);
    }

    @Test
    public void testValueOf() {
        String name = "test1";
        assertFalse(ChannelOption.exists(name));
        ChannelOption<String> option = ChannelOption.valueOf(name);
        ChannelOption<String> option2 = ChannelOption.valueOf(name);

        assertSame(option, option2);
    }

    @Test
    public void testCreateOrFail() {
        String name = "test2";
        assertFalse(ChannelOption.exists(name));
        ChannelOption<String> option = ChannelOption.newInstance(name);
        assertTrue(ChannelOption.exists(name));
        assertNotNull(option);

        try {
            ChannelOption.<String>newInstance(name);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
