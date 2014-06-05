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
 * A policy for determining when it is appropriate to remove streams from a
 * {@link Http2StreamRegistry}.
 */
public interface Http2StreamRemovalPolicy {

    /**
     * Performs the action of removing the stream.
     */
    interface Action {
        /**
         * Removes the stream from the registry.
         */
        void removeStream(Http2Stream stream);
    }

    /**
     * Sets the removal action.
     */
    void setAction(Action action);

    /**
     * Marks the given stream for removal. When this policy has determined that the given stream
     * should be removed, it will call back the {@link Action}.
     */
    void markForRemoval(Http2Stream stream);
}
