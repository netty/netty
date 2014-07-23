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
 * Provide asynchronous callback and failure notification interface
 *
 * @param <T>
 *            The type to pass to the callback
 */
public interface Http2EventListener<T> {
    /**
     * Callback will be invoked when the operation has completed
     *
     * @param obj
     *            The result of the operation
     */
    void done(T obj);

    /**
     * An exception has occurred during processing and the {@code done} method will not be invoked
     *
     * @param obj
     *            Description of the problem
     * @param expectedType
     *            The expected type if {@code done} were to be called
     */
    void fail(Throwable obj, Class<T> expectedType);
}
