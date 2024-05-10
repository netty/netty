/*
 * Copyright 2022 The Netty Project
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
package io.netty.channel.uring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectRingTest {
    @Test
    void mustBeInitiallyEmpty() {
        ObjectRing<String> ring = new ObjectRing<>();
        assertTrue(ring.isEmpty());
        assertFalse(ring.poll());
    }

    @Test
    void mustNotBeEmptyAfterPush() {
        ObjectRing<String> ring = new ObjectRing<>();
        ring.push("a", 1);
        assertFalse(ring.isEmpty());
        assertTrue(ring.poll());
    }

    @Test
    void pushAndPullAreFirstInFirstOut() {
        ObjectRing<String> ring = new ObjectRing<>();
        ring.push("a", 1);
        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("a");
        assertThat(ring.getPolledStamp()).isOne();
        assertTrue(ring.isEmpty());
        assertFalse(ring.poll());

        ring.push("b", 2);
        ring.push("c", 3);

        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("b");
        assertThat(ring.getPolledStamp()).isEqualTo(2);
        assertFalse(ring.isEmpty());

        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("c");
        assertThat(ring.getPolledStamp()).isEqualTo(3);
        assertTrue(ring.isEmpty());
        assertFalse(ring.poll());
    }

    @Test
    void mustAutomaticallyExpandRing() {
        ObjectRing<Integer> ring = new ObjectRing<>();
        for (int i = 0; i < 1000; i++) {
            ring.push(i, i);
        }
        for (int i = 0; i < 1000; i++) {
            assertTrue(ring.poll());
            assertThat(ring.getPolledObject()).isEqualTo(i);
            assertThat(ring.getPolledStamp()).isEqualTo(i);
        }
        assertTrue(ring.isEmpty());
        assertFalse(ring.poll());
    }

    @Test
    void polledItemsAreClearedAfterReading() {
        ObjectRing<String> ring = new ObjectRing<>();
        ring.push("a", 1);
        ring.push("b", 2);
        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("a");
        assertThat(ring.getPolledStamp()).isEqualTo(1L);
        assertThat(ring.getPolledObject()).isNull();
        assertThat(ring.getPolledStamp()).isZero();

        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("b");
        assertThat(ring.getPolledStamp()).isEqualTo(2L);
        assertThat(ring.getPolledObject()).isNull();
        assertThat(ring.getPolledStamp()).isZero();
    }

    @Test
    void peekOfEmptyRingReturnsFalse() {
        ObjectRing<Integer> ring = new ObjectRing<>();
        assertFalse(ring.peek());
    }

    @Test
    void peekedItemsAreClearedAfterReading() {
        ObjectRing<String> ring = new ObjectRing<>();
        ring.push("a", 1);
        ring.push("b", 2);
        assertTrue(ring.peek());
        assertThat(ring.getPolledObject()).isEqualTo("a");
        assertThat(ring.getPolledStamp()).isEqualTo(1L);
        assertThat(ring.getPolledObject()).isNull();
        assertThat(ring.getPolledStamp()).isZero();

        assertTrue(ring.peek());
        assertThat(ring.getPolledObject()).isEqualTo("a");
        assertThat(ring.getPolledStamp()).isEqualTo(1L);
        assertThat(ring.getPolledObject()).isNull();
        assertThat(ring.getPolledStamp()).isZero();

        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("a");
        assertThat(ring.getPolledStamp()).isEqualTo(1L);
        assertThat(ring.getPolledObject()).isNull();
        assertThat(ring.getPolledStamp()).isZero();

        assertTrue(ring.peek());
        assertThat(ring.getPolledObject()).isEqualTo("b");
        assertThat(ring.getPolledStamp()).isEqualTo(2L);
        assertThat(ring.getPolledObject()).isNull();
        assertThat(ring.getPolledStamp()).isZero();

        assertTrue(ring.peek());
        assertThat(ring.getPolledObject()).isEqualTo("b");
        assertThat(ring.getPolledStamp()).isEqualTo(2L);
        assertThat(ring.getPolledObject()).isNull();
        assertThat(ring.getPolledStamp()).isZero();

        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("b");
        assertThat(ring.getPolledStamp()).isEqualTo(2L);
        assertThat(ring.getPolledObject()).isNull();
        assertThat(ring.getPolledStamp()).isZero();

        assertFalse(ring.peek());
    }

    @Test
    void updatingPeekedStampMustBeReflectedInPoll() {
        ObjectRing<String> ring = new ObjectRing<>();
        ring.push("a", 1);
        ring.push("b", 2);

        assertTrue(ring.peek());
        ring.updatePeekedStamp(10 + ring.getPolledStamp());

        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("a");
        assertThat(ring.getPolledStamp()).isEqualTo(11);

        assertTrue(ring.peek());
        ring.updatePeekedStamp(10 + ring.getPolledStamp());

        assertTrue(ring.poll());
        assertThat(ring.getPolledObject()).isEqualTo("b");
        assertThat(ring.getPolledStamp()).isEqualTo(12);
    }

    @Test
    void outOfOrderRemove() {
        ObjectRing<String> ring = new ObjectRing<>();
        ring.push("a", 1);
        ring.push("b", 2);
        ring.push("c", 3);
        ring.push("d", 1);

        // Remove unknown item
        assertThat(ring.remove(10)).isNull();

        // Remove first
        assertThat(ring.remove(1)).isEqualTo("a");

        // Remove middle item
        assertThat(ring.remove(3)).isEqualTo("c");

        // Remove end item
        assertThat(ring.remove(1)).isEqualTo("d");

        // Remove previously removed item
        assertThat(ring.remove(1)).isNull();

        // Remove last item
        assertThat(ring.remove(2)).isEqualTo("b");

        // Remove when empty
        assertThat(ring.remove(1)).isNull();
    }

    @Test
    void hasNextItemIsFalseWhenEmpty() {
        ObjectRing<String> ring = new ObjectRing<>();
        assertFalse(ring.hasNextStamp(1));
    }

    @Test
    void hasNextItemIsFalseWhenStampNotMatching() {
        ObjectRing<String> ring = new ObjectRing<>();
        ring.push("a", 1);
        assertFalse(ring.hasNextStamp(2));
    }

    @Test
    void hasNextItemIsTrueWhenNextStampMatches() {
        ObjectRing<String> ring = new ObjectRing<>();
        ring.push("a", 1);
        ring.push("b", 2);
        assertTrue(ring.hasNextStamp(1));
        assertTrue(ring.poll());
        assertTrue(ring.hasNextStamp(2));
        assertTrue(ring.poll());
        assertFalse(ring.hasNextStamp(1));
        assertFalse(ring.hasNextStamp(2));
    }
}
