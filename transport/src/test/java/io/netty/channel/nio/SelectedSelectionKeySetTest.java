/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel.nio;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.channels.SelectionKey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SelectedSelectionKeySetTest {
    @Mock
    private SelectionKey mockKey;
    @Mock
    private SelectionKey mockKey2;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void addElements() {
        SelectedSelectionKeySet set = new SelectedSelectionKeySet();
        final int expectedSize = 1000000;
        for (int i = 0; i < expectedSize; ++i) {
            assertTrue(set.add(mockKey));
        }

        assertEquals(expectedSize, set.size());
        assertFalse(set.isEmpty());
    }

    @Test
    public void resetSet() {
        SelectedSelectionKeySet set = new SelectedSelectionKeySet();
        assertTrue(set.add(mockKey));
        assertTrue(set.add(mockKey2));
        set.reset(1);

        assertSame(mockKey, set.keys[0]);
        assertNull(set.keys[1]);
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
    }
}
