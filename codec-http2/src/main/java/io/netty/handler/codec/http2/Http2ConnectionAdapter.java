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

/**
 * Provides empty implementations of all {@link Http2Connection.Listener} methods.
 */
public class Http2ConnectionAdapter implements Http2Connection.Listener {

    @Override
    public void streamAdded(Http2Stream stream) {
    }

    @Override
    public void streamActive(Http2Stream stream) {
    }

    @Override
    public void streamHalfClosed(Http2Stream stream) {
    }

    @Override
    public void streamInactive(Http2Stream stream) {
    }

    @Override
    public void streamRemoved(Http2Stream stream) {
    }

    @Override
    public void streamPriorityChanged(Http2Stream stream, Http2Stream previousParent) {
    }

    @Override
    public void streamPrioritySubtreeChanged(Http2Stream stream, Http2Stream subtreeRoot) {
    }

    @Override
    public void goingAway() {
    }
}
