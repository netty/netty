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
package io.netty.channel.uring;

import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;

/**
 * {@link IoRegistration} to use with io_uring.
 */
public interface IOUringIoRegistration extends IoRegistration {

    @Override
    long submit(IoOps ops);

    void cancel();

    @Override
    IOUringHandler ioHandler();

    /**
     * Return the id of this {@link IOUringIoRegistration}. This id should be used to when creating {@link IOUringIoOps}
     * that will be submitted to this {@link IOUringIoRegistration} via {@link IOUringIoRegistration#submit(IoOps)}.
     *
     * @return  the id.
     */
    int id();
}
