/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

public interface Freeable {

    /**
     * Returns {@code true} if and only if this resource has been deallocated by {@link #free()}.
     */
    boolean isFreed();

    /**
     * Deallocates the resources.
     *
     * The result of accessing a freed resource is unspecified and can even cause JVM crash.
     *
     */
    void free();
}
