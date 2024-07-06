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
package io.netty5.channel.uring;

import io.netty5.channel.IoOps;
import io.netty5.channel.IoRegistration;

/**
 * {@link IoRegistration} to use with io_uring.
 */
public interface IoUringIoRegistration extends IoRegistration {

    /**
     * Submit a {@link IoUringIoOps} that will produce an entry on the used submission queue.
     *
     * @param   ops ops.
     * @return  the u_data of the {@link IoUringIoOps}. This can be used to cancel a previous submitted
     * {@link IoUringIoOps}.
     */
    @Override
    long submit(IoOps ops);

    @Override
    void cancel();

    @Override
    IoUringIoHandler ioHandler();
}
