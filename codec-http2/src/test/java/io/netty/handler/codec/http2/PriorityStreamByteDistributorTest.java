/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.AdditionalMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link PriorityStreamByteDistributor}.
 */
public class PriorityStreamByteDistributorTest {
    private static final int STREAM_A = 1;
    private static final int STREAM_B = 3;
    private static final int STREAM_C = 5;
    private static final int STREAM_D = 7;
    private static final int STREAM_E = 9;

    private Http2Connection connection;
    private PriorityStreamByteDistributor distributor;

    @Mock
    private StreamByteDistributor.Writer writer;

    @Before
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        connection = new DefaultHttp2Connection(false);
        distributor = new PriorityStreamByteDistributor(connection);

        // Assume we always write all the allocated bytes.
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock in) throws Throwable {
                Http2Stream stream = (Http2Stream) in.getArguments()[0];
                int numBytes = (Integer) in.getArguments()[1];
                int streamableBytes = distributor.unallocatedStreamableBytes(stream) - numBytes;
                updateStream(stream.id(), streamableBytes, streamableBytes > 0);
                return null;
            }
        }).when(writer).write(any(Http2Stream.class), anyInt());

        connection.local().createStream(STREAM_A, false);
        connection.local().createStream(STREAM_B, false);
        Http2Stream streamC = connection.local().createStream(STREAM_C, false);
        Http2Stream streamD = connection.local().createStream(STREAM_D, false);
        streamC.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
        streamD.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, false);
    }

    @Test
    public void bytesUnassignedAfterProcessing() throws Http2Exception {
        updateStream(STREAM_A, 1, true);
        updateStream(STREAM_B, 2, true);
        updateStream(STREAM_C, 3, true);
        updateStream(STREAM_D, 4, true);

        assertFalse(write(10));
        verifyWrite(STREAM_A, 1);
        verifyWrite(STREAM_B, 2);
        verifyWrite(STREAM_C, 3);
        verifyWrite(STREAM_D, 4);

        assertFalse(write(10));
        verifyWrite(STREAM_A, 0);
        verifyWrite(STREAM_B, 0);
        verifyWrite(STREAM_C, 0);
        verifyWrite(STREAM_D, 0);
    }

    @Test
    public void connectionErrorForWriterException() throws Http2Exception {
        updateStream(STREAM_A, 1, true);
        updateStream(STREAM_B, 2, true);
        updateStream(STREAM_C, 3, true);
        updateStream(STREAM_D, 4, true);

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

        doNothing().when(writer).write(same(stream(STREAM_C)), eq(3));
        write(10);
        verifyWrite(STREAM_A, 1);
        verifyWrite(STREAM_B, 2);
        verifyWrite(times(2), STREAM_C, 3);
        verifyWrite(STREAM_D, 4);
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
    public void blockedStreamShouldSpreadDataToChildren() throws Http2Exception {
        // A cannot stream.
        updateStream(STREAM_B, 10, true);
        updateStream(STREAM_C, 10, true);
        updateStream(STREAM_D, 10, true);

        // Write up to 10 bytes.
        assertTrue(write(10));

        // A is not written
        verifyWrite(STREAM_A, 0);

        // B is partially written
        verifyWrite(STREAM_B, 5);

        // Verify that C and D each shared half of A's allowance. Since A's allowance (5) cannot
        // be split evenly, one will get 3 and one will get 2.
        verifyWrite(STREAM_C, 3);
        verifyWrite(STREAM_D, 2);
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
        updateStream(STREAM_A, 10, true);
        updateStream(STREAM_C, 10, true);
        updateStream(STREAM_D, 10, true);

        // Write up to 10 bytes.
        assertTrue(write(10));

        // A is assigned all of the bytes.
        verifyWrite(STREAM_A, 10);
        verifyWrite(STREAM_B, 0);
        verifyWrite(STREAM_C, 0);
        verifyWrite(STREAM_D, 0);
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
        updateStream(STREAM_A, 5, true);
        updateStream(STREAM_C, 10, true);
        updateStream(STREAM_D, 10, true);

        // Write up to 10 bytes.
        assertTrue(write(10));

        verifyWrite(STREAM_A, 5);
        verifyWrite(STREAM_B, 0);
        verifyWrite(STREAM_C, 3);
        verifyWrite(STREAM_D, 2);
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
        updateStream(STREAM_A, 10, true);
        updateStream(STREAM_C, 10, true);
        updateStream(STREAM_D, 10, true);

        // Re-prioritize D as a direct child of the connection.
        setPriority(STREAM_D, 0, DEFAULT_PRIORITY_WEIGHT, false);

        assertTrue(write(10));

        verifyWrite(STREAM_A, 5);
        verifyWrite(STREAM_B, 0);
        verifyWrite(STREAM_C, 0);
        verifyWrite(STREAM_D, 5);
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
        updateStream(STREAM_D, writableBytes + expectedUnsentAmount, true);

        assertTrue(write(writableBytes));

        verifyWrite(STREAM_D, writableBytes);
        assertEquals(expectedUnsentAmount, streamableBytesForTree(stream(STREAM_D)));
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

        updateStream(STREAM_A, 1000, true);
        updateStream(STREAM_B, 1000, true);
        updateStream(STREAM_C, 1000, true);
        updateStream(STREAM_D, 1000, true);

        assertTrue(write(1000));

        // A is assigned all of the bytes.
        int allowedError = 10;
        verifyWriteWithDelta(STREAM_A, 109, allowedError);
        verifyWriteWithDelta(STREAM_B, 445, allowedError);
        verifyWriteWithDelta(STREAM_C, 223, allowedError);
        verifyWriteWithDelta(STREAM_D, 223, allowedError);
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

        updateStream(STREAM_A, 400, true);
        updateStream(STREAM_B, 500, true);
        updateStream(STREAM_C, 0, true);
        updateStream(STREAM_D, 700, true);

        assertTrue(write(999));

        verifyWrite(STREAM_A, 333);
        verifyWrite(STREAM_B, 333);
        verifyWrite(STREAM_C, 0);
        verifyWrite(STREAM_D, 333);
    }

    /**
     * In this test, we verify the priority bytes for each sub tree at each node are correct
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
    public void subTreeBytesShouldBeCorrect() throws Http2Exception {
        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, (Integer) 400);
        streamSizes.put(STREAM_B, (Integer) 500);
        streamSizes.put(STREAM_C, (Integer) 600);
        streamSizes.put(STREAM_D, (Integer) 700);

        updateStream(STREAM_A, streamSizes.get(STREAM_A), true);
        updateStream(STREAM_B, streamSizes.get(STREAM_B), true);
        updateStream(STREAM_C, streamSizes.get(STREAM_C), true);
        updateStream(STREAM_D, streamSizes.get(STREAM_D), true);

        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)),
                streamableBytesForTree(stream0));
        assertEquals(
                calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_A, STREAM_C, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_B)),
                streamableBytesForTree(streamB));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_C)),
                streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_D)),
                streamableBytesForTree(streamD));
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
    public void subTreeBytesShouldBeCorrectWithRestructure() throws Http2Exception {
        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, (Integer) 400);
        streamSizes.put(STREAM_B, (Integer) 500);
        streamSizes.put(STREAM_C, (Integer) 600);
        streamSizes.put(STREAM_D, (Integer) 700);

        updateStream(STREAM_A, streamSizes.get(STREAM_A), true);
        updateStream(STREAM_B, streamSizes.get(STREAM_B), true);
        updateStream(STREAM_C, streamSizes.get(STREAM_C), true);
        updateStream(STREAM_D, streamSizes.get(STREAM_D), true);

        streamB.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)),
                streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_B, STREAM_C, STREAM_D)),
                streamableBytesForTree(streamB));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_C)),
                streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_D)),
                streamableBytesForTree(streamD));
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
    public void subTreeBytesShouldBeCorrectWithAddition() throws Http2Exception {
        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        Http2Stream streamE = connection.local().createStream(STREAM_E, false);
        streamE.setPriority(STREAM_A, DEFAULT_PRIORITY_WEIGHT, true);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, (Integer) 400);
        streamSizes.put(STREAM_B, (Integer) 500);
        streamSizes.put(STREAM_C, (Integer) 600);
        streamSizes.put(STREAM_D, (Integer) 700);
        streamSizes.put(STREAM_E, (Integer) 900);

        updateStream(STREAM_A, streamSizes.get(STREAM_A), true);
        updateStream(STREAM_B, streamSizes.get(STREAM_B), true);
        updateStream(STREAM_C, streamSizes.get(STREAM_C), true);
        updateStream(STREAM_D, streamSizes.get(STREAM_D), true);
        updateStream(STREAM_E, streamSizes.get(STREAM_E), true);

        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_B, STREAM_C, STREAM_D, STREAM_E)),
                streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes,
                        Arrays.asList(STREAM_A, STREAM_E, STREAM_C, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_B)),
                streamableBytesForTree(streamB));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_C)),
                streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_D)),
                streamableBytesForTree(streamD));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_E, STREAM_C, STREAM_D)),
                streamableBytesForTree(streamE));
    }

    /**
     * In this test, we close an internal stream in the priority tree but tree should not change
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
    public void subTreeBytesShouldBeCorrectWithInternalStreamClose() throws Http2Exception {
        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, (Integer) 400);
        streamSizes.put(STREAM_B, (Integer) 500);
        streamSizes.put(STREAM_C, (Integer) 600);
        streamSizes.put(STREAM_D, (Integer) 700);

        updateStream(STREAM_A, streamSizes.get(STREAM_A), true);
        updateStream(STREAM_B, streamSizes.get(STREAM_B), true);
        updateStream(STREAM_C, streamSizes.get(STREAM_C), true);
        updateStream(STREAM_D, streamSizes.get(STREAM_D), true);

        streamA.close();

        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_B, STREAM_C, STREAM_D)),
                streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_C, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_B)),
                streamableBytesForTree(streamB));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_C)),
                streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_D)),
                streamableBytesForTree(streamD));
    }

    /**
     * In this test, we close a leaf stream in the priority tree and verify
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
    public void subTreeBytesShouldBeCorrectWithLeafStreamClose() throws Http2Exception {
        Http2Stream stream0 = connection.connectionStream();
        Http2Stream streamA = connection.stream(STREAM_A);
        Http2Stream streamB = connection.stream(STREAM_B);
        Http2Stream streamC = connection.stream(STREAM_C);
        Http2Stream streamD = connection.stream(STREAM_D);

        // Send a bunch of data on each stream.
        final IntObjectMap<Integer> streamSizes = new IntObjectHashMap<Integer>(4);
        streamSizes.put(STREAM_A, (Integer) 400);
        streamSizes.put(STREAM_B, (Integer) 500);
        streamSizes.put(STREAM_C, (Integer) 600);
        streamSizes.put(STREAM_D, (Integer) 700);

        updateStream(STREAM_A, streamSizes.get(STREAM_A), true);
        updateStream(STREAM_B, streamSizes.get(STREAM_B), true);
        updateStream(STREAM_C, streamSizes.get(STREAM_C), true);
        updateStream(STREAM_D, streamSizes.get(STREAM_D), true);

        streamC.close();

        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_A, STREAM_B, STREAM_D)),
                streamableBytesForTree(stream0));
        assertEquals(calculateStreamSizeSum(streamSizes, Arrays.asList(STREAM_A, STREAM_D)),
                streamableBytesForTree(streamA));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_B)),
                streamableBytesForTree(streamB));
        assertEquals(0, streamableBytesForTree(streamC));
        assertEquals(calculateStreamSizeSum(streamSizes, Collections.singletonList(STREAM_D)),
                streamableBytesForTree(streamD));
    }

    private Http2Stream stream(int streamId) {
        return connection.stream(streamId);
    }

    private void updateStream(final int streamId, final int streamableBytes, final boolean hasFrame) {
        final Http2Stream stream = stream(streamId);
        distributor.updateStreamableBytes(new StreamByteDistributor.StreamState() {
            @Override
            public Http2Stream stream() {
                return stream;
            }

            @Override
            public int streamableBytes() {
                return streamableBytes;
            }

            @Override
            public boolean hasFrame() {
                return hasFrame;
            }
        });
    }

    private void setPriority(int streamId, int parent, int weight, boolean exclusive) throws Http2Exception {
        stream(streamId).setPriority(parent, (short) weight, exclusive);
    }

    private long streamableBytesForTree(Http2Stream stream) {
        return distributor.unallocatedStreamableBytesForTree(stream);
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

    private void verifyWriteWithDelta(int streamId, int numBytes, int delta) {
        verify(writer).write(same(stream(streamId)), (int) AdditionalMatchers.eq(numBytes, delta));
    }

    private static long calculateStreamSizeSum(IntObjectMap<Integer> streamSizes, List<Integer> streamIds) {
        long sum = 0;
        for (Integer streamId : streamIds) {
            Integer streamSize = streamSizes.get(streamId);
            if (streamSize != null) {
                sum += streamSize;
            }
        }
        return sum;
    }
}
