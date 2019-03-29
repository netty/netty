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
package io.netty.util.concurrent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class SaturationHistogram {
  private final AtomicLongArray histogram;
  private final AtomicInteger count;
  private final AtomicLong fullySaturatedTimeNanos;
  private final AtomicLong fullySaturatedStart;
  private final int nThreads;

  public SaturationHistogram(int nThreads) {
      this.nThreads = nThreads;
      this.count = new AtomicInteger();
      this.fullySaturatedTimeNanos = new AtomicLong();
      this.fullySaturatedStart = new AtomicLong();
      this.histogram = new AtomicLongArray(nThreads);
  }

  public long[] snapshot() {
      long[] snap = new long[histogram.length()];
      for (int i = 0; i < histogram.length(); i++) {
          snap[i] = histogram.get(i);
      }
      return snap;
  }

  public long getFullySaturatedTimeNanos() {
      return fullySaturatedTimeNanos.get();
  }

  public void enter() {
      int currentCount = count.incrementAndGet();
      if (currentCount <= nThreads) {
          histogram.incrementAndGet(currentCount - 1);
          if (currentCount == nThreads) {
              fullySaturatedStart.set(System.nanoTime());
          }
      }
  }

  public void exit() {
      long currentFullySaturatedStart = fullySaturatedStart.getAndSet(0L);
      int oldCount = count.getAndDecrement();
      if (oldCount == nThreads && currentFullySaturatedStart != 0) {
          fullySaturatedTimeNanos.addAndGet(System.nanoTime() - currentFullySaturatedStart);
      }
  }
}
