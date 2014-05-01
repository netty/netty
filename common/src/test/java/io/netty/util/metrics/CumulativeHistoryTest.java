/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util.metrics;

import org.junit.Test;
import static org.junit.Assert.*;

public class CumulativeHistoryTest {
    @Test
    public void testRemember0() {
        CumulativeHistory history = new CumulativeHistory(1);

        history.update(100);
        history.update(100);
        history.update(200);
        history.update(400);
        assertEquals(0, history.value());
        history.tick();
        assertEquals(800, history.value());
        history.update(200);
        history.update(300);
        history.tick();
        assertEquals(500, history.value());
        history.tick();
        assertEquals(0, history.value());
    }

    @Test
    public void testRemember5() {
        CumulativeHistory history = new CumulativeHistory(5);

        history.update(100);
        history.update(100);
        assertEquals(0, history.value());
        history.tick();
        history.update(200);
        history.tick();
        assertEquals(400, history.value());
        history.update(100);
        assertEquals(400, history.value());
        history.update(100);
        history.tick();
        assertEquals(600, history.value());
        history.tick();
        assertEquals(600, history.value());
        history.tick();
        assertEquals(600, history.value());

        history.tick();
        assertEquals(400, history.value());
        history.tick();
        assertEquals(200, history.value());
        history.tick();
        assertEquals(0, history.value());
        history.tick();
        assertEquals(0, history.value());
        history.tick();
        assertEquals(0, history.value());
    }

    @Test
    public void testMissingUpdates() {
        CumulativeHistory history = new CumulativeHistory(10);
        for (int i = 0; i < 30; i++) {
            history.tick();
            assertEquals(0, history.value());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSize() {
        CumulativeHistory history = new CumulativeHistory(0);
    }
}
