/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.group;

import io.netty.util.concurrent.GenericFutureListener;

/**
 * Listens to the result of a {@link ChannelGroupFuture}.  The result of the
 * asynchronous {@link ChannelGroup} I/O operations is notified once this
 * listener is added by calling {@link ChannelGroupFuture#addListener(GenericFutureListener)}
 * and all I/O operations are complete.
 */
public interface ChannelGroupFutureListener extends GenericFutureListener<ChannelGroupFuture> {

}
