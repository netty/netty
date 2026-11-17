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
package io.netty.handler.codec.quic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuicStreamIdGeneratorTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testRollbackRestoresMostRecentlyAllocatedIdAndReusesIt(boolean bidirectional) {
        QuicStreamIdGenerator generator = new QuicStreamIdGenerator(false);
        long streamId = generator.nextStreamId(bidirectional);

        assertTrue(generator.rollbackStreamId(streamId, bidirectional));
        // After the rollback the same id must be handed out again, so a failed creation
        // does not permanently consume it.
        assertEquals(streamId, generator.nextStreamId(bidirectional));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testRollbackOfIdThatIsNotTheFrontierFails(boolean bidirectional) {
        QuicStreamIdGenerator generator = new QuicStreamIdGenerator(false);
        long first = generator.nextStreamId(bidirectional);
        long second = generator.nextStreamId(bidirectional);

        // Only the most recently allocated id of a direction may be rolled back, as older
        // ids may already have an open stream in quiche.
        assertFalse(generator.rollbackStreamId(first, bidirectional));
        // A failed rollback must not move the frontier.
        assertEquals(second + 4, generator.nextStreamId(bidirectional));
    }

    @Test
    public void testRollbackIsIndependentPerDirection() {
        QuicStreamIdGenerator generator = new QuicStreamIdGenerator(false);
        long uni = generator.nextStreamId(false);
        long bidi = generator.nextStreamId(true);

        assertTrue(generator.rollbackStreamId(uni, false));
        // Rolling back the unidirectional frontier must not affect the bidirectional one,
        // which keeps advancing, while the rolled-back unidirectional id is handed out again.
        assertEquals(bidi + 4, generator.nextStreamId(true));
        assertEquals(uni, generator.nextStreamId(false));
    }
}
