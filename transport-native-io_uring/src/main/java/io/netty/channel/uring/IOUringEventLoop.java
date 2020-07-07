/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.LongObjectHashMap;
import io.netty.util.concurrent.RejectedExecutionHandler;

import java.util.HashMap;
import java.util.concurrent.Executor;

class IOUringEventLoop extends SingleThreadEventLoop {


    private final IntObjectMap<AbstractIOUringChannel> channels = new IntObjectHashMap<AbstractIOUringChannel>(4096);
    // events should be unique to identify which event type that was
    private long eventIdCounter;
    private final LongObjectHashMap<Event> events = new LongObjectHashMap<Event>();

    protected IOUringEventLoop(final EventLoopGroup parent, final Executor executor, final boolean addTaskWakesUp,
            final int maxPendingTasks, final RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, addTaskWakesUp, maxPendingTasks, rejectedExecutionHandler);
    }

    public long incrementEventIdCounter() {
        long eventId = eventIdCounter;
        eventIdCounter++;
        return eventId;
    }

    public void addNewEvent(Event event) {
        events.put(event.getId(), event);
    }

    @Override
    protected void run() {
        //Todo
        for (;;) {
            // wait until an event has finished
            //final long cqe = Native.ioUringWaitCqe(io_uring);
            //final Event event = events.get(Native.ioUringGetEventId(cqe));
            //final int ret = Native.ioUringGetRes(cqe);
            // switch (event.getOp()) {
            //     case ACCEPT:
            //         // serverChannel is necessary to call newChildchannel
            //         // create a new accept event
            //         break;
            //     case READ:
            //         // need to save the Bytebuf before I execute the read operation
            //         // fireChannelRead(byteBuf)
            //         break;
            //     case WRITE:
            //         // you have to store Bytebuf to continue writing
            //         break;
            // }
            // processing Tasks
        }
    }
}
