/*
 * Copyright 2021 The Netty Project
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
package io.netty.util.internal.telemetry;

import org.junit.Test;

import static org.junit.Assert.*;

public class CycleBufferQueueEliminationStrategyTest {
    private static final int ROUGH_THRESHOLD_NANOS = 100000000;

    @Test
    public void partialOccupancyTest() {
        final CycleBufferQueueEliminationStrategy cycleBufferQueueEliminationStrategy =
                new CycleBufferQueueEliminationStrategy(5);
        cycleBufferQueueEliminationStrategy.handleElemination(new EleminationHookQueueEntry<Integer>(1));
        assertEquals(1, cycleBufferQueueEliminationStrategy.getLatencies().length);
        assertTrue(cycleBufferQueueEliminationStrategy.getLatencies()[0] < ROUGH_THRESHOLD_NANOS);
        cycleBufferQueueEliminationStrategy.handleElemination(new EleminationHookQueueEntry<Integer>(1));
        assertEquals(2, cycleBufferQueueEliminationStrategy.getLatencies().length);
    }

    @Test
    public void fullOccupancyTest() {
        final int bufferSize = 5;
        final CycleBufferQueueEliminationStrategy cycleBufferQueueEliminationStrategy =
                new CycleBufferQueueEliminationStrategy(bufferSize);
        for (int i = 1; i <= 2 * bufferSize; i++) {
            cycleBufferQueueEliminationStrategy.handleElemination(new EleminationHookQueueEntry<Integer>(i));
            assertEquals(Math.min(i, bufferSize), cycleBufferQueueEliminationStrategy.getLatencies().length);
        }
        assertEquals(bufferSize, cycleBufferQueueEliminationStrategy.getLatencies().length);
        int bufferOverflowSize = 2;
        for (int i = 0; i < 2; i++) {
            cycleBufferQueueEliminationStrategy.handleElemination(new EleminationHookQueueEntry<Integer>(0, 0));
        }
        assertEquals(bufferSize, cycleBufferQueueEliminationStrategy.getLatencies().length);
        for (int i = 0; i < bufferSize - bufferOverflowSize; i++) {
            assertTrue(cycleBufferQueueEliminationStrategy.getLatencies()[i] < 100000000);
        }
        for (int i = bufferSize - bufferOverflowSize; i < bufferSize; i++) {
            assertTrue(cycleBufferQueueEliminationStrategy.getLatencies()[i] > 100000000);
        }
    }
}
