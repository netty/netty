/*
 * Copyright 2017 The Netty Project
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
package io.netty.channel.nio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SelectedSelectionKeySetTest {
    @Mock
    private SelectionKey mockKey;
    @Mock
    private SelectionKey mockKey2;

    @Mock
    private SelectionKey mockKey3;

    @BeforeEach
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

    @Test
    public void iterator() {
        SelectedSelectionKeySet set = new SelectedSelectionKeySet();
        assertTrue(set.add(mockKey));
        assertTrue(set.add(mockKey2));
        Iterator<SelectionKey> keys = set.iterator();
        assertTrue(keys.hasNext());
        assertSame(mockKey, keys.next());
        assertTrue(keys.hasNext());
        assertSame(mockKey2, keys.next());
        assertFalse(keys.hasNext());

        try {
            keys.next();
            fail();
        } catch (NoSuchElementException expected) {
            // expected
        }

        try {
            keys.remove();
            fail();
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void contains() {
        SelectedSelectionKeySet set = new SelectedSelectionKeySet();
        assertTrue(set.add(mockKey));
        assertTrue(set.add(mockKey2));
        assertFalse(set.contains(mockKey));
        assertFalse(set.contains(mockKey2));
        assertFalse(set.contains(mockKey3));
    }

    @Test
    public void remove() {
        SelectedSelectionKeySet set = new SelectedSelectionKeySet();
        assertTrue(set.add(mockKey));
        assertFalse(set.remove(mockKey));
        assertFalse(set.remove(mockKey2));
    }
}
