/*
 * Copyright 2017 The Netty Project
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_WEIGHT;
import static io.netty.handler.codec.http2.WeightedFairQueueByteDistributor.INITIAL_CHILDREN_MAP_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;

public class WeightedFairQueueByteDistributorDependencyTreeTest extends
                                                AbstractWeightedFairQueueByteDistributorDependencyTest {
    private static final int leadersId = 3; // js, css
    private static final int unblockedId = 5;
    private static final int backgroundId = 7;
    private static final int speculativeId = 9;
    private static final int followersId = 11; // images
    private static final short leadersWeight = 201;
    private static final short unblockedWeight = 101;
    private static final short backgroundWeight = 1;
    private static final short speculativeWeight = 1;
    private static final short followersWeight = 1;

    @BeforeEach
    public void setup() throws Http2Exception {
        MockitoAnnotations.initMocks(this);

        setup(0);
    }

    private void setup(int maxStateOnlySize) {
        connection = new DefaultHttp2Connection(false);
        distributor = new WeightedFairQueueByteDistributor(connection, maxStateOnlySize);

        // Assume we always write all the allocated bytes.
        doAnswer(writeAnswer(false)).when(writer).write(any(Http2Stream.class), anyInt());
    }

    @Test
    public void closingStreamWithChildrenDoesNotCauseConcurrentModification() throws Http2Exception {
        // We create enough streams to wrap around the child array. We carefully craft the stream ids so that they hash
        // codes overlap with respect to the child collection. If the implementation is not careful this may lead to a
        // concurrent modification exception while promoting all children to the connection stream.
        final Http2Stream streamA = connection.local().createStream(1, false);
        final int numStreams = INITIAL_CHILDREN_MAP_SIZE - 1;
        for (int i = 0, streamId = 3; i < numStreams; ++i, streamId += INITIAL_CHILDREN_MAP_SIZE) {
            final Http2Stream stream = connection.local().createStream(streamId, false);
            setPriority(stream.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        }
        assertEquals(INITIAL_CHILDREN_MAP_SIZE, connection.numActiveStreams());
        streamA.close();
        assertEquals(numStreams, connection.numActiveStreams());
    }

    @Test
    public void closeWhileIteratingDoesNotNPE() throws Http2Exception {
        final Http2Stream streamA = connection.local().createStream(3, false);
        final Http2Stream streamB = connection.local().createStream(5, false);
        final Http2Stream streamC = connection.local().createStream(7, false);
        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        connection.forEachActiveStream(new Http2StreamVisitor() {
            @Override
            public boolean visit(Http2Stream stream) throws Http2Exception {
                streamA.close();
                setPriority(streamB.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
                return true;
            }
        });
    }

    @Test
    public void localStreamCanDependUponIdleStream() throws Http2Exception {
        setup(1);

        Http2Stream streamA = connection.local().createStream(1, false);
        setPriority(3, streamA.id(), MIN_WEIGHT, true);
        assertTrue(distributor.isChild(3, streamA.id(), MIN_WEIGHT));
    }

    @Test
    public void remoteStreamCanDependUponIdleStream() throws Http2Exception {
        setup(1);

        Http2Stream streamA = connection.remote().createStream(2, false);
        setPriority(4, streamA.id(), MIN_WEIGHT, true);
        assertTrue(distributor.isChild(4, streamA.id(), MIN_WEIGHT));
    }

    @Test
    public void prioritizeShouldUseDefaults() throws Exception {
        Http2Stream stream = connection.local().createStream(1, false);
        assertTrue(distributor.isChild(stream.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));
        assertEquals(0, distributor.numChildren(stream.id()));
    }

    @Test
    public void reprioritizeWithNoChangeShouldDoNothing() throws Exception {
        Http2Stream stream = connection.local().createStream(1, false);
        setPriority(stream.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertTrue(distributor.isChild(stream.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));
        assertEquals(0, distributor.numChildren(stream.id()));
    }

    @Test
    public void stateOnlyPriorityShouldBePreservedWhenStreamsAreCreatedAndClosed() throws Http2Exception {
        setup(3);

        short weight3 = MIN_WEIGHT + 1;
        short weight5 = (short) (weight3 + 1);
        short weight7 = (short) (weight5 + 1);
        setPriority(3, connection.connectionStream().id(), weight3, true);
        setPriority(5, connection.connectionStream().id(), weight5, true);
        setPriority(7, connection.connectionStream().id(), weight7, true);

        assertEquals(0, connection.numActiveStreams());
        verifyStateOnlyPriorityShouldBePreservedWhenStreamsAreCreated(weight3, weight5, weight7);

        // Now create stream objects and ensure the state and dependency tree is preserved.
        Http2Stream streamA = connection.local().createStream(3, false);
        Http2Stream streamB = connection.local().createStream(5, false);
        Http2Stream streamC = connection.local().createStream(7, false);

        assertEquals(3, connection.numActiveStreams());
        verifyStateOnlyPriorityShouldBePreservedWhenStreamsAreCreated(weight3, weight5, weight7);

        // Close all the streams and ensure the state and dependency tree is preserved.
        streamA.close();
        streamB.close();
        streamC.close();

        assertEquals(0, connection.numActiveStreams());
        verifyStateOnlyPriorityShouldBePreservedWhenStreamsAreCreated(weight3, weight5, weight7);
    }

    private void verifyStateOnlyPriorityShouldBePreservedWhenStreamsAreCreated(short weight3, short weight5,
                                                                               short weight7) {
        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(7, connection.connectionStream().id(), weight7));
        assertEquals(1, distributor.numChildren(7));

        // Level 2
        assertTrue(distributor.isChild(5, 7, weight5));
        assertEquals(1, distributor.numChildren(5));

        // Level 3
        assertTrue(distributor.isChild(3, 5, weight3));
        assertEquals(0, distributor.numChildren(3));
    }

    @Test
    public void fireFoxQoSStreamsRemainAfterDataStreamsAreClosed() throws Http2Exception {
        // https://bitsup.blogspot.com/2015/01/http2-dependency-priorities-in-firefox.html
        setup(5);

        setPriority(leadersId, connection.connectionStream().id(), leadersWeight, false);
        setPriority(unblockedId, connection.connectionStream().id(), unblockedWeight, false);
        setPriority(backgroundId, connection.connectionStream().id(), backgroundWeight, false);
        setPriority(speculativeId, backgroundId, speculativeWeight, false);
        setPriority(followersId, leadersId, followersWeight, false);

        verifyFireFoxQoSStreams();

        // Simulate a HTML request
        short htmlGetStreamWeight = 2;
        Http2Stream htmlGetStream = connection.local().createStream(13, false);
        setPriority(htmlGetStream.id(), followersId, htmlGetStreamWeight, false);
        Http2Stream favIconStream = connection.local().createStream(15, false);
        setPriority(favIconStream.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT, false);
        Http2Stream cssStream = connection.local().createStream(17, false);
        setPriority(cssStream.id(), leadersId, DEFAULT_PRIORITY_WEIGHT, false);
        Http2Stream jsStream = connection.local().createStream(19, false);
        setPriority(jsStream.id(), leadersId, DEFAULT_PRIORITY_WEIGHT, false);
        Http2Stream imageStream = connection.local().createStream(21, false);
        setPriority(imageStream.id(), followersId, 1, false);

        // Level 0
        assertEquals(4, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(leadersId, connection.connectionStream().id(), leadersWeight));
        assertEquals(3, distributor.numChildren(leadersId));

        assertTrue(distributor.isChild(unblockedId, connection.connectionStream().id(), unblockedWeight));
        assertEquals(0, distributor.numChildren(unblockedId));

        assertTrue(distributor.isChild(backgroundId, connection.connectionStream().id(), backgroundWeight));
        assertEquals(1, distributor.numChildren(backgroundId));

        assertTrue(distributor.isChild(favIconStream.id(), connection.connectionStream().id(),
                                       DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(favIconStream.id()));

        // Level 2
        assertTrue(distributor.isChild(followersId, leadersId, followersWeight));
        assertEquals(2, distributor.numChildren(followersId));

        assertTrue(distributor.isChild(speculativeId, backgroundId, speculativeWeight));
        assertEquals(0, distributor.numChildren(speculativeId));

        assertTrue(distributor.isChild(cssStream.id(), leadersId, DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(cssStream.id()));

        assertTrue(distributor.isChild(jsStream.id(), leadersId, DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(jsStream.id()));

        // Level 3
        assertTrue(distributor.isChild(htmlGetStream.id(), followersId, htmlGetStreamWeight));
        assertEquals(0, distributor.numChildren(htmlGetStream.id()));

        assertTrue(distributor.isChild(imageStream.id(), followersId, followersWeight));
        assertEquals(0, distributor.numChildren(imageStream.id()));

        // Close all the data streams and ensure the "priority only streams" are retained in the dependency tree.
        htmlGetStream.close();
        favIconStream.close();
        cssStream.close();
        jsStream.close();
        imageStream.close();

        verifyFireFoxQoSStreams();
    }

    private void verifyFireFoxQoSStreams() {
        // Level 0
        assertEquals(3, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(leadersId, connection.connectionStream().id(), leadersWeight));
        assertEquals(1, distributor.numChildren(leadersId));

        assertTrue(distributor.isChild(unblockedId, connection.connectionStream().id(), unblockedWeight));
        assertEquals(0, distributor.numChildren(unblockedId));

        assertTrue(distributor.isChild(backgroundId, connection.connectionStream().id(), backgroundWeight));
        assertEquals(1, distributor.numChildren(backgroundId));

        // Level 2
        assertTrue(distributor.isChild(followersId, leadersId, followersWeight));
        assertEquals(0, distributor.numChildren(followersId));

        assertTrue(distributor.isChild(speculativeId, backgroundId, speculativeWeight));
        assertEquals(0, distributor.numChildren(speculativeId));
    }

    @Test
    public void lowestPrecedenceStateShouldBeDropped() throws Http2Exception {
        setup(3);

        short weight3 = MAX_WEIGHT;
        short weight5 = (short) (weight3 - 1);
        short weight7 = (short) (weight5 - 1);
        short weight9 = (short) (weight7 - 1);
        setPriority(3, connection.connectionStream().id(), weight3, true);
        setPriority(5, connection.connectionStream().id(), weight5, true);
        setPriority(7, connection.connectionStream().id(), weight7, false);
        assertEquals(0, connection.numActiveStreams());
        verifyLowestPrecedenceStateShouldBeDropped1(weight3, weight5, weight7);

        // Attempt to create a new item in the dependency tree but the maximum amount of "state only" streams is meet
        // so a stream will have to be dropped. Currently the new stream is the lowest "precedence" so it is dropped.
        setPriority(9, 3, weight9, false);
        assertEquals(0, connection.numActiveStreams());
        verifyLowestPrecedenceStateShouldBeDropped1(weight3, weight5, weight7);

        // Set the priority for stream 9 such that its depth in the dependency tree is numerically lower than stream 3,
        // and therefore the dependency state associated with stream 3 will be dropped.
        setPriority(9, 5, weight9, true);
        verifyLowestPrecedenceStateShouldBeDropped2(weight9, weight5, weight7);

        // Test that stream which has been activated is lower priority than other streams that have not been activated.
        Http2Stream streamA = connection.local().createStream(5, false);
        streamA.close();
        verifyLowestPrecedenceStateShouldBeDropped2(weight9, weight5, weight7);

        // Stream 3 (hasn't been opened) should result in stream 5 being dropped.
        setPriority(3, 9, weight3, false);
        verifyLowestPrecedenceStateShouldBeDropped3(weight3, weight7, weight9);

        // Stream 5's state has been discarded so we should be able to re-insert this state.
        setPriority(5, 0, weight5, false);
        verifyLowestPrecedenceStateShouldBeDropped4(weight5, weight7, weight9);

        // All streams are at the same level, so stream ID should be used to drop the numeric lowest valued stream.
        short weight11 = (short) (weight9 - 1);
        setPriority(11, 0, weight11, false);
        verifyLowestPrecedenceStateShouldBeDropped5(weight7, weight9, weight11);
    }

    private void verifyLowestPrecedenceStateShouldBeDropped1(short weight3, short weight5, short weight7) {
        // Level 0
        assertEquals(2, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(7, connection.connectionStream().id(), weight7));
        assertEquals(0, distributor.numChildren(7));

        assertTrue(distributor.isChild(5, connection.connectionStream().id(), weight5));
        assertEquals(1, distributor.numChildren(5));

        // Level 2
        assertTrue(distributor.isChild(3, 5, weight3));
        assertEquals(0, distributor.numChildren(3));
    }

    private void verifyLowestPrecedenceStateShouldBeDropped2(short weight9, short weight5, short weight7) {
        // Level 0
        assertEquals(2, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(7, connection.connectionStream().id(), weight7));
        assertEquals(0, distributor.numChildren(7));

        assertTrue(distributor.isChild(5, connection.connectionStream().id(), weight5));
        assertEquals(1, distributor.numChildren(5));

        // Level 2
        assertTrue(distributor.isChild(9, 5, weight9));
        assertEquals(0, distributor.numChildren(9));
    }

    private void verifyLowestPrecedenceStateShouldBeDropped3(short weight3, short weight7, short weight9) {
        // Level 0
        assertEquals(2, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(7, connection.connectionStream().id(), weight7));
        assertEquals(0, distributor.numChildren(7));

        assertTrue(distributor.isChild(9, connection.connectionStream().id(), weight9));
        assertEquals(1, distributor.numChildren(9));

        // Level 2
        assertTrue(distributor.isChild(3, 9, weight3));
        assertEquals(0, distributor.numChildren(3));
    }

    private void verifyLowestPrecedenceStateShouldBeDropped4(short weight5, short weight7, short weight9) {
        // Level 0
        assertEquals(3, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(5, connection.connectionStream().id(), weight5));
        assertEquals(0, distributor.numChildren(5));

        assertTrue(distributor.isChild(7, connection.connectionStream().id(), weight7));
        assertEquals(0, distributor.numChildren(7));

        assertTrue(distributor.isChild(9, connection.connectionStream().id(), weight9));
        assertEquals(0, distributor.numChildren(9));
    }

    private void verifyLowestPrecedenceStateShouldBeDropped5(short weight7, short weight9, short weight11) {
        // Level 0
        assertEquals(3, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(11, connection.connectionStream().id(), weight11));
        assertEquals(0, distributor.numChildren(11));

        assertTrue(distributor.isChild(7, connection.connectionStream().id(), weight7));
        assertEquals(0, distributor.numChildren(7));

        assertTrue(distributor.isChild(9, connection.connectionStream().id(), weight9));
        assertEquals(0, distributor.numChildren(9));
    }

    @Test
    public void priorityOnlyStreamsArePreservedWhenReservedStreamsAreClosed() throws Http2Exception {
        setup(1);

        short weight3 = MIN_WEIGHT;
        setPriority(3, connection.connectionStream().id(), weight3, true);

        Http2Stream streamA = connection.local().createStream(5, false);
        Http2Stream streamB = connection.remote().reservePushStream(4, streamA);

        // Level 0
        assertEquals(3, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(3, connection.connectionStream().id(), weight3));
        assertEquals(0, distributor.numChildren(3));

        assertTrue(distributor.isChild(streamA.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamA.id()));

        assertTrue(distributor.isChild(streamB.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));

        // Close both streams.
        streamB.close();
        streamA.close();

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(3, connection.connectionStream().id(), weight3));
        assertEquals(0, distributor.numChildren(3));
    }

    @Test
    public void insertExclusiveShouldAddNewLevel() throws Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, connection.numActiveStreams());

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamA.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamA.id()));

        // Level 2
        assertTrue(distributor.isChild(streamD.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamD.id()));

        // Level 3
        assertTrue(distributor.isChild(streamB.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));

        assertTrue(distributor.isChild(streamC.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamC.id()));
    }

    @Test
    public void existingChildMadeExclusiveShouldNotCreateTreeCycle() throws Http2Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Stream C is already dependent on Stream A, but now make that an exclusive dependency
        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, connection.numActiveStreams());

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamA.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamA.id()));

        // Level 2
        assertTrue(distributor.isChild(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamC.id()));

        // Level 3
        assertTrue(distributor.isChild(streamB.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));

        assertTrue(distributor.isChild(streamD.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamD.id()));
    }

    @Test
    public void newExclusiveChildShouldUpdateOldParentCorrectly() throws Http2Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);
        Http2Stream streamE = connection.local().createStream(9, false);
        Http2Stream streamF = connection.local().createStream(11, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamF.id(), streamE.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // F is now going to be exclusively dependent on A, after this we should check that stream E
        // prioritizableForTree is not over decremented.
        setPriority(streamF.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(6, connection.numActiveStreams());

        // Level 0
        assertEquals(2, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamE.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamE.id()));

        assertTrue(distributor.isChild(streamA.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamA.id()));

        // Level 2
        assertTrue(distributor.isChild(streamF.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamF.id()));

        // Level 3
        assertTrue(distributor.isChild(streamB.id(), streamF.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));

        assertTrue(distributor.isChild(streamC.id(), streamF.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamC.id()));

        // Level 4
        assertTrue(distributor.isChild(streamD.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamD.id()));
    }

    @Test
    public void weightChangeWithNoTreeChangeShouldBeRespected() throws Http2Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, connection.numActiveStreams());

        short newWeight = (short) (DEFAULT_PRIORITY_WEIGHT + 1);
        setPriority(streamD.id(), streamA.id(), newWeight, false);

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamA.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamA.id()));

        // Level 2
        assertTrue(distributor.isChild(streamD.id(), streamA.id(), newWeight));
        assertEquals(2, distributor.numChildren(streamD.id()));

        // Level 3
        assertTrue(distributor.isChild(streamB.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));

        assertTrue(distributor.isChild(streamC.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamC.id()));
    }

    @Test
    public void sameNodeDependentShouldNotStackOverflowNorChangePrioritizableForTree() throws Http2Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        boolean[] exclusives = { true, false };
        short[] weights = { DEFAULT_PRIORITY_WEIGHT, 100, 200, DEFAULT_PRIORITY_WEIGHT };

        assertEquals(4, connection.numActiveStreams());

        // The goal is to call setPriority with the same parent and vary the parameters
        // we were at one point adding a circular depends to the tree and then throwing
        // a StackOverflow due to infinite recursive operation.
        for (short weight : weights) {
            for (boolean exclusive : exclusives) {
                setPriority(streamD.id(), streamA.id(), weight, exclusive);

                assertEquals(0, distributor.numChildren(streamB.id()));
                assertEquals(0, distributor.numChildren(streamC.id()));
                assertEquals(1, distributor.numChildren(streamA.id()));
                assertEquals(2, distributor.numChildren(streamD.id()));
                assertFalse(distributor.isChild(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
                assertFalse(distributor.isChild(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
                assertTrue(distributor.isChild(streamB.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
                assertTrue(distributor.isChild(streamC.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
                assertTrue(distributor.isChild(streamD.id(), streamA.id(), weight));
            }
        }
    }

    @Test
    public void multipleCircularDependencyShouldUpdatePrioritizable() throws Http2Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        assertEquals(4, connection.numActiveStreams());

        // Bring B to the root
        setPriority(streamA.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, true);

        // Move all streams to be children of B
        setPriority(streamC.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Move A back to the root
        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, true);

        // Move all streams to be children of A
        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamA.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(3, distributor.numChildren(streamA.id()));

        // Level 2
        assertTrue(distributor.isChild(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));

        assertTrue(distributor.isChild(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamC.id()));

        assertTrue(distributor.isChild(streamD.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamD.id()));
    }

    @Test
    public void removeWithPrioritizableDependentsShouldNotRestructureTree() throws Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Default removal policy will cause it to be removed immediately.
        streamB.close();

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamA.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamA.id()));

        // Level 2
        assertTrue(distributor.isChild(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamC.id()));

        assertTrue(distributor.isChild(streamD.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamD.id()));
    }

    @Test
    public void closeWithNoPrioritizableDependentsShouldRestructureTree() throws Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);
        Http2Stream streamE = connection.local().createStream(9, false);
        Http2Stream streamF = connection.local().createStream(11, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamE.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamF.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Close internal nodes, leave 1 leaf node open, the only remaining stream is the one that is not closed (E).
        streamA.close();
        streamB.close();
        streamC.close();
        streamD.close();
        streamF.close();

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamE.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamE.id()));
    }

    @Test
    public void priorityChangeWithNoPrioritizableDependentsShouldRestructureTree() throws Exception {
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);
        Http2Stream streamE = connection.local().createStream(9, false);
        Http2Stream streamF = connection.local().createStream(11, false);

        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamC.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamD.id(), streamB.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamF.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);
        setPriority(streamE.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Leave leaf nodes open (E & F)
        streamA.close();
        streamB.close();
        streamC.close();
        streamD.close();

        // Move F to depend on C, even though C is closed.
        setPriority(streamF.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Level 0
        assertEquals(2, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamE.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamE.id()));

        assertTrue(distributor.isChild(streamF.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamF.id()));
    }

    @Test
    public void circularDependencyShouldRestructureTree() throws Exception {
        // Using example from https://tools.ietf.org/html/rfc7540#section-5.3.3
        // Initialize all the nodes
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);
        Http2Stream streamE = connection.local().createStream(9, false);
        Http2Stream streamF = connection.local().createStream(11, false);

        assertEquals(6, distributor.numChildren(connection.connectionStream().id()));
        assertEquals(0, distributor.numChildren(streamA.id()));
        assertEquals(0, distributor.numChildren(streamB.id()));
        assertEquals(0, distributor.numChildren(streamC.id()));
        assertEquals(0, distributor.numChildren(streamD.id()));
        assertEquals(0, distributor.numChildren(streamE.id()));
        assertEquals(0, distributor.numChildren(streamF.id()));

        // Build the tree
        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(5, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamA.id()));

        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(4, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamA.id()));

        setPriority(streamD.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(3, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamD.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamC.id()));

        setPriority(streamE.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(2, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamE.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamC.id()));

        setPriority(streamF.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamF.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamD.id()));

        assertEquals(6, connection.numActiveStreams());

        // Non-exclusive re-prioritization of a->d.
        setPriority(streamA.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamD.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamD.id()));

        // Level 2
        assertTrue(distributor.isChild(streamF.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamF.id()));

        assertTrue(distributor.isChild(streamA.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamA.id()));

        // Level 3
        assertTrue(distributor.isChild(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));

        assertTrue(distributor.isChild(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamC.id()));

        // Level 4
        assertTrue(distributor.isChild(streamE.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamE.id()));
    }

    @Test
    public void circularDependencyWithExclusiveShouldRestructureTree() throws Exception {
        // Using example from https://tools.ietf.org/html/rfc7540#section-5.3.3
        // Initialize all the nodes
        Http2Stream streamA = connection.local().createStream(1, false);
        Http2Stream streamB = connection.local().createStream(3, false);
        Http2Stream streamC = connection.local().createStream(5, false);
        Http2Stream streamD = connection.local().createStream(7, false);
        Http2Stream streamE = connection.local().createStream(9, false);
        Http2Stream streamF = connection.local().createStream(11, false);

        assertEquals(6, distributor.numChildren(connection.connectionStream().id()));
        assertEquals(0, distributor.numChildren(streamA.id()));
        assertEquals(0, distributor.numChildren(streamB.id()));
        assertEquals(0, distributor.numChildren(streamC.id()));
        assertEquals(0, distributor.numChildren(streamD.id()));
        assertEquals(0, distributor.numChildren(streamE.id()));
        assertEquals(0, distributor.numChildren(streamF.id()));

        // Build the tree
        setPriority(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(5, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamA.id()));

        setPriority(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(4, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamA.id()));

        setPriority(streamD.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(3, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamD.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamC.id()));

        setPriority(streamE.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(2, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamE.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(2, distributor.numChildren(streamC.id()));

        setPriority(streamF.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT, false);
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));
        assertTrue(distributor.isChild(streamF.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamD.id()));

        assertEquals(6, connection.numActiveStreams());

        // Exclusive re-prioritization of a->d.
        setPriority(streamA.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT, true);

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamD.id(), connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamD.id()));

        // Level 2
        assertTrue(distributor.isChild(streamA.id(), streamD.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(3, distributor.numChildren(streamA.id()));

        // Level 3
        assertTrue(distributor.isChild(streamB.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));

        assertTrue(distributor.isChild(streamF.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamF.id()));

        assertTrue(distributor.isChild(streamC.id(), streamA.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamC.id()));

        // Level 4;
        assertTrue(distributor.isChild(streamE.id(), streamC.id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamE.id()));
    }

    // Unknown parent streams can come about in two ways:
    //  1. Because the stream is old and its state was purged
    //  2. This is the first reference to the stream, as implied at least by RFC7540§5.3.1:
    //    > A dependency on a stream that is not currently in the tree — such as a stream in the
    //    > "idle" state — results in that stream being given a default priority
    @Test
    public void unknownParentShouldBeCreatedUnderConnection() throws Exception {
        setup(5);

        // Purposefully avoid creating streamA's Http2Stream so that is it completely unknown.
        // It shouldn't matter whether the ID is before or after streamB.id()
        int streamAId = 1;
        Http2Stream streamB = connection.local().createStream(3, false);

        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));
        assertEquals(0, distributor.numChildren(streamB.id()));

        // Build the tree
        setPriority(streamB.id(), streamAId, DEFAULT_PRIORITY_WEIGHT, false);

        assertEquals(1, connection.numActiveStreams());

        // Level 0
        assertEquals(1, distributor.numChildren(connection.connectionStream().id()));

        // Level 1
        assertTrue(distributor.isChild(streamAId, connection.connectionStream().id(), DEFAULT_PRIORITY_WEIGHT));
        assertEquals(1, distributor.numChildren(streamAId));

        // Level 2
        assertTrue(distributor.isChild(streamB.id(), streamAId, DEFAULT_PRIORITY_WEIGHT));
        assertEquals(0, distributor.numChildren(streamB.id()));
    }
}
