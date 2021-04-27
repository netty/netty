/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class WeightedFairQueueByteDistributorTest extends AbstractWeightedFairQueueByteDistributorDependencyTest {
    private static final int STREAM_A = 1;
    private static final int STREAM_B = 3;
    private static final int STREAM_C = 5;
    private static final int STREAM_D = 7;
    private static final int STREAM_E = 9;
    private static final int ALLOCATION_QUANTUM = 100;

    @Before
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        // Assume we always write all the allocated bytes.
        doAnswer(writeAnswer(false)).when(writer).write(any(Http2Stream.class), anyInt());

        setup(-1);
    }

    private void setup(int maxStateOnlySize) throws Http2Exception {
        connection = new DefaultHttp2Connection(false);
        distributor = maxStateOnlySize >= 0 ? new WeightedFairQueueByteDistributor(connection, maxStateOnlySize)
                                            : new WeightedFairQueueByteDistributor(connection);
        distributor.allocationQuantum(ALLOCATION_QUANTUM);

        connection.local().createStream(STREAM_A, false);
        connection.local().createStream(STREAM_B, false);
        Http2Stream streamC = connection.local().createStream(STREAM_C, false);
        Http2Stream streamD = connection.local().createStream(STREAM_D, false);
        setPriority(streamC.id(), STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
    }

    /**
     * In this test, we block B such that it has no frames. We distribute enough bytes for all streams and stream B
     * should be preserved in the priority queue structure until it has no "active" children, but it should not be
     * doubly added to stream 0.
     *
     * <pre>
     *         0
     *         |
     *         A
     *         |
     *        [B]
     *         |
     *         C
     *         |
     *         D
     * </pre>
     *
     * After the write:
     * <pre>
     *         0
     * </pre>
     */
    @Test
    public void writeWithNonActiveStreamShouldNotDobuleAddToPriorityQueue() throws Http2Exception {
        initState(STREAM_A, 400, true);
        initState(STREAM_B, 500, true);
        initState(STREAM_C, 600, true);
        initState(STREAM_D, 700, true);

        setPriority(STREAM_B, STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);
        setPriority(STREAM_D, STREAM_C, DEFAULT_PRIORITY_WEIGHT, true);

        // Block B, but it should still remain in the queue/tree structure.
        initState(STREAM_B, 0, false);

        // Get the streams before the write, because they may be be closed.
        Http2Stream streamA = stream(STREAM_A);
        Http2Stream streamB = stream(STREAM_B);
        Http2Stream streamC = stream(STREAM_C);
        Http2Stream streamD = stream(STREAM_D);

        reset(writer);
        doAnswer(writeAnswer(true)).when(writer).write(any(Http2Stream.class), anyInt());

        assertFalse(write(400 + 600 + 700));
        assertEquals(400, captureWrites(streamA));
        verifyNeverWrite(streamB);
        assertEquals(600, captureWrites(streamC));
        assertEquals(700, captureWrites(streamD));
    }

    @Test
    public void bytesUnassignedAfterProcessing() throws Http2Exception {
        initState(STREAM_A, 1, true);
        initState(STREAM_B, 2, true);
        initState(STREAM_C, 3, true);
        initState(STREAM_D, 4, true);

        assertFalse(write(10));
        verifyWrite(STREAM_A, 1);
        verifyWrite(STREAM_B, 2);
        verifyWrite(STREAM_C, 3);
        verifyWrite(STREAM_D, 4);

        assertFalse(write(10));
        verifyAnyWrite(STREAM_A, 1);
        verifyAnyWrite(STREAM_B, 1);
        verifyAnyWrite(STREAM_C, 1);
        verifyAnyWrite(STREAM_D, 1);
    }

    @Test
    public void connectionErrorForWriterException() throws Http2Exception {
        initState(STREAM_A, 1, true);
        initState(STREAM_B, 2, true);
        initState(STREAM_C, 3, true);
        initState(STREAM_D, 4, true);

        Exception fakeException = new RuntimeException("Fake exception");
        doThrow(fakeException).when(writer).write(same(stream(STREAM_C)), eq(3));

        try {
            write(10);
            fail("Expected an exception");
        } catch (Http2Exception e) {
            assertFalse(Http2Exception.isStreamError(e));
            assertEquals(Http2Error.INTERNAL_ERROR, e.error());
            assertSame(fakeException, e.getCause());
        }

        verifyWrite(atMost(1), STREAM_A, 1);
        verifyWrite(atMost(1), STREAM_B, 2);
        verifyWrite(STREAM_C, 3);
        verifyWrite(atMost(1), STREAM_D, 4);

        doAnswer(writeAnswer(false)).when(writer).write(same(stream(STREAM_C)), eq(3));
        assertFalse(write(10));
        verifyWrite(STREAM_A, 1);
        verifyWrite(STREAM_B, 2);
        verifyWrite(times(2), STREAM_C, 3);
        verifyWrite(STREAM_D, 4);
    }

    /**
     * In this test, we verify that each stream is allocated a minimum chunk size. When bytes
     * run out, the remaining streams will be next in line for the next iteration.
     */
    @Test
    public void minChunkShouldBeAllocatedPerStream() throws Http2Exception {
        // Re-assign weights.
        setPriority(STREAM_A, 0, (short) 50, false);
        setPriority(STREAM_B, 0, (short) 200, false);
        setPriority(STREAM_C, STREAM_A, (short) 100, false);
        setPriority(STREAM_D, STREAM_A, (short) 100, false);

        // Update the streams.
        initState(STREAM_A, ALLOCATION_QUANTUM, true);
        initState(STREAM_B, ALLOCATION_QUANTUM, true);
        initState(STREAM_C, ALLOCATION_QUANTUM, true);
        initState(STREAM_D, ALLOCATION_QUANTUM, true);

        // Only write 3 * chunkSize, so that we'll only write to the first 3 streams.
        int written = 3 * ALLOCATION_QUANTUM;
        assertTrue(write(written));
        assertEquals(ALLOCATION_QUANTUM, captureWrites(STREAM_A));
        assertEquals(ALLOCATION_QUANTUM, captureWrites(STREAM_B));
        assertEquals(ALLOCATION_QUANTUM, captureWrites(STREAM_C));
        verifyWrite(atMost(1), STREAM_D, 0);

        // Now write again and verify that the last stream is written to.
        assertFalse(write(ALLOCATION_QUANTUM));
        assertEquals(ALLOCATION_QUANTUM, captureWrites(STREAM_A));
        assertEquals(ALLOCATION_QUANTUM, captureWrites(STREAM_B));
        assertEquals(ALLOCATION_QUANTUM, captureWrites(STREAM_C));
        assertEquals(ALLOCATION_QUANTUM, captureWrites(STREAM_D));
    }

    /**
     * In this test, we verify that the highest priority frame which has 0 bytes to send, but an empty frame is able
     * to send that empty frame.
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     *
     * <pre>
     *         0
     *         |
     *         A
     *         |
     *         B
     *        / \
     *       C   D
     * </pre>
     */
    @Test
    public void emptyFrameAtHeadIsWritten() throws Http2Exception {
        initState(STREAM_A, 0, true);
        initState(STREAM_B, 0, true);
        initState(STREAM_C, 0, true);
        initState(STREAM_D, 10, true);

        setPriority(STREAM_B, STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);

        assertFalse(write(10));
        verifyWrite(STREAM_A, 0);
        verifyWrite(STREAM_B, 0);
        verifyWrite(STREAM_C, 0);
        verifyWrite(STREAM_D, 10);
    }

    /**
     * In this test, we block A which allows bytes to be written by C and D. Here's a view of the tree (stream A is
     * blocked).
     *
     * <pre>
     *         0
     *        / \
     *      [A]  B
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void blockedStreamNoDataShouldSpreadDataToChildren() throws Http2Exception {
        blockedStreamShouldSpreadDataToChildren(false);
    }

    /**
     * In this test, we block A and also give it an empty data frame to send.
     * All bytes should be delegated to by C and D. Here's a view of the tree (stream A is blocked).
     *
     * <pre>
     *           0
     *         /   \
     *      [A](0)  B
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void blockedStreamWithDataAndNotAllowedToSendShouldSpreadDataToChildren() throws Http2Exception {
        // A cannot stream.
        initState(STREAM_A, 0, true, false);
        blockedStreamShouldSpreadDataToChildren(false);
    }

    /**
     * In this test, we allow A to send, but expect the flow controller will only write to the stream 1 time.
     * This is because we give the stream a chance to write its empty frame 1 time, and the stream will not
     * be written to again until a update stream is called.
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void streamWithZeroFlowControlWindowAndDataShouldWriteOnlyOnce() throws Http2Exception {
        initState(STREAM_A, 0, true, true);
        blockedStreamShouldSpreadDataToChildren(true);

        // Make sure if we call update stream again, A should write 1 more time.
        initState(STREAM_A, 0, true, true);
        assertFalse(write(1));
        verifyWrite(times(2), STREAM_A, 0);

        // Try to write again, but since no initState A should not write again
        assertFalse(write(1));
        verifyWrite(times(2), STREAM_A, 0);
    }

    private void blockedStreamShouldSpreadDataToChildren(boolean streamAShouldWriteZero) throws Http2Exception {
        initState(STREAM_B, 10, true);
        initState(STREAM_C, 10, true);
        initState(STREAM_D, 10, true);

        // Write up to 10 bytes.
        assertTrue(write(10));

        if (streamAShouldWriteZero) {
            verifyWrite(STREAM_A, 0);
        } else {
            verifyNeverWrite(STREAM_A);
        }
        verifyWrite(atMost(1), STREAM_C, 0);
        verifyWrite(atMost(1), STREAM_D, 0);

        // B is entirely written
        verifyWrite(STREAM_B, 10);

        // Now test that writes get delegated from A (which is blocked) to its children
        assertTrue(write(5));
        if (streamAShouldWriteZero) {
            verifyWrite(times(1), STREAM_A, 0);
        } else {
            verifyNeverWrite(STREAM_A);
        }
        verifyWrite(STREAM_D, 5);
        verifyWrite(atMost(1), STREAM_C, 0);

        assertTrue(write(5));
        if (streamAShouldWriteZero) {
            verifyWrite(times(1), STREAM_A, 0);
        } else {
            verifyNeverWrite(STREAM_A);
        }
        assertEquals(10, captureWrites(STREAM_C) + captureWrites(STREAM_D));

        assertTrue(write(5));
        assertFalse(write(5));
        if (streamAShouldWriteZero) {
            verifyWrite(times(1), STREAM_A, 0);
        } else {
            verifyNeverWrite(STREAM_A);
        }
        verifyWrite(times(2), STREAM_C, 5);
        verifyWrite(times(2), STREAM_D, 5);
    }

    /**
     * In this test, we block B which allows all bytes to be written by A. A should not share the data with its children
     * since it's not blocked.
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void childrenShouldNotSendDataUntilParentBlocked() throws Http2Exception {
        // B cannot stream.
        initState(STREAM_A, 10, true);
        initState(STREAM_C, 10, true);
        initState(STREAM_D, 10, true);

        // Write up to 10 bytes.
        assertTrue(write(10));

        // A is assigned all of the bytes.
        verifyWrite(STREAM_A, 10);
        verifyNeverWrite(STREAM_B);
        verifyWrite(atMost(1), STREAM_C, 0);
        verifyWrite(atMost(1), STREAM_D, 0);
    }

    /**
     * In this test, we block B which allows all bytes to be written by A. Once A is complete, it will spill over the
     * remaining of its portion to its children.
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void parentShouldWaterFallDataToChildren() throws Http2Exception {
        // B cannot stream.
        initState(STREAM_A, 5, true);
        initState(STREAM_C, 10, true);
        initState(STREAM_D, 10, true);

        // Write up to 10 bytes.
        assertTrue(write(10));

        verifyWrite(STREAM_A, 5);
        verifyNeverWrite(STREAM_B);
        verifyWrite(STREAM_C, 5);
        verifyNeverWrite(STREAM_D);

        assertFalse(write(15));
        verifyAnyWrite(STREAM_A, 1);
        verifyNeverWrite(STREAM_B);
        verifyWrite(times(2), STREAM_C, 5);
        verifyWrite(STREAM_D, 10);
    }

    /**
     * In this test, we verify re-prioritizing a stream. We start out with B blocked:
     *
     * <pre>
     *         0
     *        / \
     *       A  [B]
     *      / \
     *     C   D
     * </pre>
     *
     * We then re-prioritize D so that it's directly off of the connection and verify that A and D split the written
     * bytes between them.
     *
     * <pre>
     *           0
     *          /|\
     *        /  |  \
     *       A  [B]  D
     *      /
     *     C
     * </pre>
     */
    @Test
    public void reprioritizeShouldAdjustOutboundFlow() throws Http2Exception {
        // B cannot stream.
        initState(STREAM_A, 10, true);
        initState(STREAM_C, 10, true);
        initState(STREAM_D, 10, true);

        // Re-prioritize D as a direct child of the connection.
        setPriority(STREAM_D, 0, DEFAULT_PRIORITY_WEIGHT, false);

        assertTrue(write(10));

        verifyWrite(STREAM_A, 10);
        verifyNeverWrite(STREAM_B);
        verifyNeverWrite(STREAM_C);
        verifyWrite(atMost(1), STREAM_D, 0);

        assertFalse(write(20));
        verifyAnyWrite(STREAM_A, 1);
        verifyNeverWrite(STREAM_B);
        verifyWrite(STREAM_C, 10);
        verifyWrite(STREAM_D, 10);
    }

    /**
     * Test that the maximum allowed amount the flow controller allows to be sent is always fully allocated if
     * the streams have at least this much data to send. See https://github.com/netty/netty/issues/4266.
     * <pre>
     *            0
     *          / | \
     *        /   |   \
     *      A(0) B(0) C(0)
     *     /
     *    D(> allowed to send in 1 allocation attempt)
     * </pre>
     */
    @Test
    public void unstreamableParentsShouldFeedHungryChildren() throws Http2Exception {
        // Setup the priority tree.
        setPriority(STREAM_A, 0, (short) 32, false);
        setPriority(STREAM_B, 0, (short) 16, false);
        setPriority(STREAM_C, 0, (short) 16, false);
        setPriority(STREAM_D, STREAM_A, (short) 16, false);

        final int writableBytes = 100;

        // Send enough so it can not be completely written out
        final int expectedUnsentAmount = 1;
        initState(STREAM_D, writableBytes + expectedUnsentAmount, true);

        assertTrue(write(writableBytes));
        verifyWrite(STREAM_D, writableBytes);

        assertFalse(write(expectedUnsentAmount));
        verifyWrite(STREAM_D, expectedUnsentAmount);
    }

    /**
     * In this test, we root all streams at the connection, and then verify that data is split appropriately based on
     * weight (all available data is the same).
     *
     * <pre>
     *           0
     *        / / \ \
     *       A B   C D
     * </pre>
     */
    @Test
    public void writeShouldPreferHighestWeight() throws Http2Exception {
        // Root the streams at the connection and assign weights.
        setPriority(STREAM_A, 0, (short) 50, false);
        setPriority(STREAM_B, 0, (short) 200, false);
        setPriority(STREAM_C, 0, (short) 100, false);
        setPriority(STREAM_D, 0, (short) 100, false);

        initState(STREAM_A, 1000, true);
        initState(STREAM_B, 1000, true);
        initState(STREAM_C, 1000, true);
        initState(STREAM_D, 1000, true);

        // Set allocation quantum to 1 so it is easier to see the ratio of total bytes written between each stream.
        distributor.allocationQuantum(1);
        assertTrue(write(1000));

        assertEquals(100, captureWrites(STREAM_A));
        assertEquals(450, captureWrites(STREAM_B));
        assertEquals(225, captureWrites(STREAM_C));
        assertEquals(225, captureWrites(STREAM_D));
    }

    /**
     * In this test, we root all streams at the connection, block streams C and D, and then verify that data is
     * prioritized toward stream B which has a higher weight than stream A.
     * <p>
     * We also verify that the amount that is written is not uniform, and not always the allocation quantum.
     *
     * <pre>
     *            0
     *        / /  \  \
     *       A B   [C] [D]
     * </pre>
     */
    @Test
    public void writeShouldFavorPriority() throws Http2Exception {
        // Root the streams at the connection and assign weights.
        setPriority(STREAM_A, 0, (short) 50, false);
        setPriority(STREAM_B, 0, (short) 200, false);
        setPriority(STREAM_C, 0, (short) 100, false);
        setPriority(STREAM_D, 0, (short) 100, false);

        initState(STREAM_A, 1000, true);
        initState(STREAM_B, 1000, true);
        initState(STREAM_C, 1000, false);
        initState(STREAM_D, 1000, false);

        // Set allocation quantum to 1 so it is easier to see the ratio of total bytes written between each stream.
        distributor.allocationQuantum(1);

        assertTrue(write(100));
        assertEquals(20, captureWrites(STREAM_A));
        verifyWrite(times(20), STREAM_A, 1);
        assertEquals(80, captureWrites(STREAM_B));
        verifyWrite(times(0), STREAM_B, 1);
        verifyNeverWrite(STREAM_C);
        verifyNeverWrite(STREAM_D);

        assertTrue(write(100));
        assertEquals(40, captureWrites(STREAM_A));
        verifyWrite(times(40), STREAM_A, 1);
        assertEquals(160, captureWrites(STREAM_B));
        verifyWrite(atMost(1), STREAM_B, 1);
        verifyNeverWrite(STREAM_C);
        verifyNeverWrite(STREAM_D);

        assertTrue(write(1050));
        assertEquals(250, captureWrites(STREAM_A));
        verifyWrite(times(250), STREAM_A, 1);
        assertEquals(1000, captureWrites(STREAM_B));
        verifyWrite(atMost(2), STREAM_B, 1);
        verifyNeverWrite(STREAM_C);
        verifyNeverWrite(STREAM_D);

        assertFalse(write(750));
        assertEquals(1000, captureWrites(STREAM_A));
        verifyWrite(times(1), STREAM_A, 750);
        assertEquals(1000, captureWrites(STREAM_B));
        verifyWrite(times(0), STREAM_B, 0);
        verifyNeverWrite(STREAM_C);
        verifyNeverWrite(STREAM_D);
    }

    /**
     * In this test, we root all streams at the connection, and then verify that data is split equally among the stream,
     * since they all have the same weight.
     *
     * <pre>
     *           0
     *        / / \ \
     *       A B   C D
     * </pre>
     */
    @Test
    public void samePriorityShouldDistributeBasedOnData() throws Http2Exception {
        // Root the streams at the connection with the same weights.
        setPriority(STREAM_A, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_B, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_C, 0, DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(STREAM_D, 0, DEFAULT_PRIORITY_WEIGHT, false);

        initState(STREAM_A, 400, true);
        initState(STREAM_B, 500, true);
        initState(STREAM_C, 0, true);
        initState(STREAM_D, 700, true);

        // Set allocation quantum to 1 so it is easier to see the ratio of total bytes written between each stream.
        distributor.allocationQuantum(1);
        assertTrue(write(999));

        assertEquals(333, captureWrites(STREAM_A));
        assertEquals(333, captureWrites(STREAM_B));
        verifyWrite(times(1), STREAM_C, 0);
        assertEquals(333, captureWrites(STREAM_D));
    }

    /**
     * In this test, we call distribute with 0 bytes and verify that all streams with 0 bytes are written.
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     *
     * <pre>
     *         0
     *         |
     *        [A]
     *         |
     *         B
     *        / \
     *       C   D
     * </pre>
     */
    @Test
    public void zeroDistributeShouldWriteAllZeroFrames() throws Http2Exception {
        initState(STREAM_A, 400, false);
        initState(STREAM_B, 0, true);
        initState(STREAM_C, 0, true);
        initState(STREAM_D, 0, true);

        setPriority(STREAM_B, STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);

        assertFalse(write(0));
        verifyNeverWrite(STREAM_A);
        verifyWrite(STREAM_B, 0);
        verifyAnyWrite(STREAM_B, 1);
        verifyWrite(STREAM_C, 0);
        verifyAnyWrite(STREAM_C, 1);
        verifyWrite(STREAM_D, 0);
        verifyAnyWrite(STREAM_D, 1);
    }

    /**
     * In this test, we call distribute with 100 bytes which is the total amount eligible to be written, and also have
     * streams with 0 bytes to write. All of these streams should be written with a single call to distribute.
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     *
     * <pre>
     *         0
     *         |
     *        [A]
     *         |
     *         B
     *        / \
     *       C   D
     * </pre>
     */
    @Test
    public void nonZeroDistributeShouldWriteAllZeroFramesIfAllEligibleDataIsWritten() throws Http2Exception {
        initState(STREAM_A, 400, false);
        initState(STREAM_B, 100, true);
        initState(STREAM_C, 0, true);
        initState(STREAM_D, 0, true);

        setPriority(STREAM_B, STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);

        assertFalse(write(100));
        verifyNeverWrite(STREAM_A);
        verifyWrite(STREAM_B, 100);
        verifyAnyWrite(STREAM_B, 1);
        verifyWrite(STREAM_C, 0);
        verifyAnyWrite(STREAM_C, 1);
        verifyWrite(STREAM_D, 0);
        verifyAnyWrite(STREAM_D, 1);
    }

    /**
     * In this test, we shift the priority tree and verify priority bytes for each subtree are correct
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     *
     * <pre>
     *         0
     *         |
     *         A
     *         |
     *         B
     *        / \
     *       C   D
     * </pre>
     */
    @Test
    public void bytesDistributedWithRestructureShouldBeCorrect() throws Http2Exception {
        initState(STREAM_A, 400, true);
        initState(STREAM_B, 500, true);
        initState(STREAM_C, 600, true);
        initState(STREAM_D, 700, true);

        setPriority(STREAM_B, STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);

        assertTrue(write(500));
        assertEquals(400, captureWrites(STREAM_A));
        verifyWrite(STREAM_B, 100);
        verifyNeverWrite(STREAM_C);
        verifyNeverWrite(STREAM_D);

        assertTrue(write(400));
        assertEquals(400, captureWrites(STREAM_A));
        assertEquals(500, captureWrites(STREAM_B));
        verifyWrite(atMost(1), STREAM_C, 0);
        verifyWrite(atMost(1), STREAM_D, 0);

        assertFalse(write(1300));
        assertEquals(400, captureWrites(STREAM_A));
        assertEquals(500, captureWrites(STREAM_B));
        assertEquals(600, captureWrites(STREAM_C));
        assertEquals(700, captureWrites(STREAM_D));
    }

    /**
     * In this test, we add a node to the priority tree and verify
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the tree shift:
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *       |
     *       E
     *      / \
     *     C   D
     * </pre>
     */
    @Test
    public void bytesDistributedWithAdditionShouldBeCorrect() throws Http2Exception {
        Http2Stream streamE = connection.local().createStream(STREAM_E, false);
        setPriority(streamE.id(), STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);

        // Send a bunch of data on each stream.
        initState(STREAM_A, 400, true);
        initState(STREAM_B, 500, true);
        initState(STREAM_C, 600, true);
        initState(STREAM_D, 700, true);
        initState(STREAM_E, 900, true);

        assertTrue(write(900));
        assertEquals(400, captureWrites(STREAM_A));
        assertEquals(500, captureWrites(STREAM_B));
        verifyNeverWrite(STREAM_C);
        verifyNeverWrite(STREAM_D);
        verifyWrite(atMost(1), STREAM_E, 0);

        assertTrue(write(900));
        assertEquals(400, captureWrites(STREAM_A));
        assertEquals(500, captureWrites(STREAM_B));
        verifyWrite(atMost(1), STREAM_C, 0);
        verifyWrite(atMost(1), STREAM_D, 0);
        assertEquals(900, captureWrites(STREAM_E));

        assertFalse(write(1301));
        assertEquals(400, captureWrites(STREAM_A));
        assertEquals(500, captureWrites(STREAM_B));
        assertEquals(600, captureWrites(STREAM_C));
        assertEquals(700, captureWrites(STREAM_D));
        assertEquals(900, captureWrites(STREAM_E));
    }

    /**
     * In this test, we close an internal stream in the priority tree.
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the close:
     * <pre>
     *          0
     *        / | \
     *       C  D  B
     * </pre>
     */
    @Test
    public void bytesDistributedShouldBeCorrectWithInternalStreamClose() throws Http2Exception {
        initState(STREAM_A, 400, true);
        initState(STREAM_B, 500, true);
        initState(STREAM_C, 600, true);
        initState(STREAM_D, 700, true);

        stream(STREAM_A).close();

        assertTrue(write(500));
        verifyNeverWrite(STREAM_A);
        assertEquals(500, captureWrites(STREAM_B) + captureWrites(STREAM_C) + captureWrites(STREAM_D));

        assertFalse(write(1300));
        verifyNeverWrite(STREAM_A);
        assertEquals(500, captureWrites(STREAM_B));
        assertEquals(600, captureWrites(STREAM_C));
        assertEquals(700, captureWrites(STREAM_D));
    }

    /**
     * In this test, we close a leaf stream in the priority tree and verify distribution.
     *
     * <pre>
     *         0
     *        / \
     *       A   B
     *      / \
     *     C   D
     * </pre>
     *
     * After the close:
     * <pre>
     *         0
     *        / \
     *       A   B
     *       |
     *       D
     * </pre>
     */
    @Test
    public void bytesDistributedShouldBeCorrectWithLeafStreamClose() throws Http2Exception {
        initState(STREAM_A, 400, true);
        initState(STREAM_B, 500, true);
        initState(STREAM_C, 600, true);
        initState(STREAM_D, 700, true);

        stream(STREAM_C).close();

        assertTrue(write(900));
        assertEquals(400, captureWrites(STREAM_A));
        assertEquals(500, captureWrites(STREAM_B));
        verifyNeverWrite(STREAM_C);
        verifyWrite(atMost(1), STREAM_D, 0);

        assertFalse(write(700));
        assertEquals(400, captureWrites(STREAM_A));
        assertEquals(500, captureWrites(STREAM_B));
        verifyNeverWrite(STREAM_C);
        assertEquals(700, captureWrites(STREAM_D));
    }

    @Test
    public void activeStreamDependentOnNewNonActiveStreamGetsQuantum() throws Http2Exception {
        setup(0);
        initState(STREAM_D, 700, true);
        setPriority(STREAM_D, STREAM_E, DEFAULT_PRIORITY_WEIGHT, true);

        assertFalse(write(700));
        assertEquals(700, captureWrites(STREAM_D));
    }

    @Test
    public void streamWindowLargerThanIntDoesNotInfiniteLoop() throws Http2Exception {
        initState(STREAM_A, Integer.MAX_VALUE + 1L, true, true);
        assertTrue(write(Integer.MAX_VALUE));
        verifyWrite(STREAM_A, Integer.MAX_VALUE);
        assertFalse(write(1));
        verifyWrite(STREAM_A, 1);
    }

    private boolean write(int numBytes) throws Http2Exception {
        return distributor.distribute(numBytes, writer);
    }

    private void verifyWrite(int streamId, int numBytes) {
        verify(writer).write(same(stream(streamId)), eq(numBytes));
    }

    private void verifyWrite(VerificationMode mode, int streamId, int numBytes) {
        verify(writer, mode).write(same(stream(streamId)), eq(numBytes));
    }

    private void verifyAnyWrite(int streamId, int times) {
        verify(writer, times(times)).write(same(stream(streamId)), anyInt());
    }

    private void verifyNeverWrite(int streamId) {
        verifyNeverWrite(stream(streamId));
    }

    private void verifyNeverWrite(Http2Stream stream) {
        verify(writer, never()).write(same(stream), anyInt());
    }

    private int captureWrites(int streamId) {
        return captureWrites(stream(streamId));
    }

    private int captureWrites(Http2Stream stream) {
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(writer, atLeastOnce()).write(same(stream), captor.capture());
        int total = 0;
        for (Integer x : captor.getAllValues()) {
            total += x;
        }
        return total;
    }
}
