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

import io.netty.util.internal.UnstableApi;

/**
 * A visitor that allows iteration over a collection of streams.
 */
@UnstableApi
public interface Http2StreamVisitor {
    /**
     * @return <ul>
     *         <li>{@code true} if the visitor wants to continue the loop and handle the entry.</li>
     *         <li>{@code false} if the visitor wants to stop handling headers and abort the loop.</li>
     *         </ul>
     */
    boolean visit(Http2Stream stream) throws Http2Exception;
}
