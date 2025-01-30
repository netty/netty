/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel;

/**
 * A registration for IO.
 *
 */
public interface IoRegistration {

    /**
     * Implementation specific attachment, which might be {@code null}.
     *
     * @return  attachment.
     */
    <T> T attachment();

    /**
     * Submit the {@link IoOps} to the registration.
     *
     * @param   ops ops.
     * @return  an identifier for the operation, which might be unique or not (depending on the implementation).
     */
    long submit(IoOps ops);

    /**
     * Returns {@code true} if the registration is still valid. Once {@link #cancel()} is called this
     * will return {@code false}.
     *
     * @return  valid.
     */
    boolean isValid();

    /**
     * Cancel the registration.
     *
     * @return {@code true} if cancellation was successful, {@code false} otherwise.
     */
    boolean cancel();
}
