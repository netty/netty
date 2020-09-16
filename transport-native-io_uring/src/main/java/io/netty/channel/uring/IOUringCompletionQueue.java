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

import io.netty.util.internal.PlatformDependent;

final class IOUringCompletionQueue {

  //these offsets are used to access specific properties
  //CQE (https://github.com/axboe/liburing/blob/master/src/include/liburing/io_uring.h#L162)
  private static final int CQE_USER_DATA_FIELD = 0;
  private static final int CQE_RES_FIELD = 8;
  private static final int CQE_FLAGS_FIELD = 12;

  private static final int CQE_SIZE = 16;

  //these unsigned integer pointers(shared with the kernel) will be changed by the kernel
  private final long kHeadAddress;
  private final long kTailAddress;
  private final long kringMaskAddress;
  private final long kringEntries;
  private final long kOverflowAddress;

  private final long completionQueueArrayAddress;

  final int ringSize;
  final long ringAddress;
  final int ringFd;

  private final int ringMask;
  private int ringHead;

  IOUringCompletionQueue(long kHeadAddress, long kTailAddress, long kringMaskAddress, long kringEntries,
      long kOverflowAddress, long completionQueueArrayAddress, int ringSize, long ringAddress, int ringFd) {
    this.kHeadAddress = kHeadAddress;
    this.kTailAddress = kTailAddress;
    this.kringMaskAddress = kringMaskAddress;
    this.kringEntries = kringEntries;
    this.kOverflowAddress = kOverflowAddress;
    this.completionQueueArrayAddress = completionQueueArrayAddress;
    this.ringSize = ringSize;
    this.ringAddress = ringAddress;
    this.ringFd = ringFd;

    this.ringMask = PlatformDependent.getIntVolatile(kringMaskAddress);
    this.ringHead = PlatformDependent.getIntVolatile(kHeadAddress);
  }

  public boolean hasCompletions() {
      return ringHead != PlatformDependent.getIntVolatile(kTailAddress);
  }

  int process(IOUringCompletionQueueCallback callback) {
      int tail = PlatformDependent.getIntVolatile(kTailAddress);
      int i = 0;
      while (ringHead != tail) {
          long cqeAddress = completionQueueArrayAddress + (ringHead & ringMask) * CQE_SIZE;

          long udata = PlatformDependent.getLong(cqeAddress + CQE_USER_DATA_FIELD);
          int res = PlatformDependent.getInt(cqeAddress + CQE_RES_FIELD);
          int flags = PlatformDependent.getInt(cqeAddress + CQE_FLAGS_FIELD);

          //Ensure that the kernel only sees the new value of the head index after the CQEs have been read.
          ringHead++;
          PlatformDependent.putIntOrdered(kHeadAddress, ringHead);

          int fd = (int) (udata >>> 32);
          int opMask = (int) (udata & 0xFFFFFFFFL);
          int op = opMask >>> 16;
          int mask = opMask & 0xffff;

          i++;
          callback.handle(fd, res, flags, op, mask);
      }
      return i;
  }

  interface IOUringCompletionQueueCallback {
      void handle(int fd, int res, int flags, int op, int mask);
  }

  boolean ioUringWaitCqe() {
    //IORING_ENTER_GETEVENTS -> wait until an event is completely processed
    int ret = Native.ioUringEnter(ringFd, 0, 1, Native.IORING_ENTER_GETEVENTS);
    if (ret < 0) {
        //Todo throw exception!
        return false;
    } else if (ret == 0) {
        return true;
    }
    //Todo throw Exception!
    return false;
  }
}
