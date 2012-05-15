/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.http;

import static org.junit.Assert.*;
import static io.netty.channel.socket.http.SaturationStateChange.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests saturation managers
 * 

 */
public class SaturationManagerTest {

    private SaturationManager manager;

    @Before
    public void setUp() {
        manager = new SaturationManager(100L, 200L);
    }

    @Test
    public void testQueueSizeChanged() {
        assertEquals(NO_CHANGE, manager.queueSizeChanged(100L));
        assertEquals(NO_CHANGE, manager.queueSizeChanged(99L));
        assertEquals(NO_CHANGE, manager.queueSizeChanged(1L));
        assertEquals(SATURATED, manager.queueSizeChanged(1L));
        assertEquals(NO_CHANGE, manager.queueSizeChanged(10L));

        assertEquals(NO_CHANGE, manager.queueSizeChanged(-10L));
        assertEquals(NO_CHANGE, manager.queueSizeChanged(-1L));
        assertEquals(NO_CHANGE, manager.queueSizeChanged(-1L));
        assertEquals(DESATURATED, manager.queueSizeChanged(-99L));
        assertEquals(NO_CHANGE, manager.queueSizeChanged(-100L));
    }
}
