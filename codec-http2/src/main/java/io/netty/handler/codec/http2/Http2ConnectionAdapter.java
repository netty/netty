/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.UnstableApi;

/**
 * Provides empty implementations of all {@link Http2Connection.Listener} methods.
 */
@UnstableApi
public class Http2ConnectionAdapter implements Http2Connection.Listener {

    @Override
    public void onStreamAdded(Http2Stream stream) {
    }

    @Override
    public void onStreamActive(Http2Stream stream) {
    }

    @Override
    public void onStreamHalfClosed(Http2Stream stream) {
    }

    @Override
    public void onStreamClosed(Http2Stream stream) {
    }

    @Override
    public void onStreamRemoved(Http2Stream stream) {
    }

    @Override
    public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {
    }

    @Override
    public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {
    }

    @Override
    public void onPriorityTreeParentChanged(Http2Stream stream, Http2Stream oldParent) {
    }

    @Override
    public void onPriorityTreeParentChanging(Http2Stream stream, Http2Stream newParent) {
    }

    @Override
    public void onWeightChanged(Http2Stream stream, short oldWeight) {
    }
}
