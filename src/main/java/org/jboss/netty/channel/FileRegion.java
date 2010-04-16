/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import org.jboss.netty.util.ExternalResourceReleasable;

/**
 * A region of a file that is sent via a {@link Channel} which supports
 * <a href="http://en.wikipedia.org/wiki/Zero-copy">zero-copy file transfer</a>.
 *
 * <h3>Upgrade your JDK / JRE</h3>
 *
 * {@link FileChannel#transferTo(long, long, WritableByteChannel)} has at least
 * four known bugs in the old versions of Sun JDK and perhaps its derived ones.
 * Please upgrade your JDK to 1.6.0_18 or later version if you are going to use
 * zero-copy file transfer.
 * <ul>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5103988">5103988</a>
 *   - FileChannel.transferTo() should return -1 for EAGAIN instead throws IOException</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6253145">6253145</a>
 *   - FileChannel.transferTo() on Linux fails when going beyond 2GB boundary</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6427312">6427312</a>
 *   - FileChannel.transferTo() throws IOException "system call interrupted"</li>
 * <li><a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6524172">6470086</a>
 *   - FileChannel.transferTo(2147483647, 1, channel) causes "Value too large" exception</li>
 * </ul>
 *
 * <h3>Check your operating system and JDK / JRE</h3>
 *
 * If your operating system (or JDK / JRE) does not support zero-copy file
 * transfer, sending a file with {@link FileRegion} might fail or yield worse
 * performance.  For example, sending a large file doesn't work well in Windows.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface FileRegion extends ExternalResourceReleasable {

    /**
     * Returns the offset in the file where the transfer began.
     */
    long getPosition();

    /**
     * Returns the number of bytes to transfer.
     */
    long getCount();

    /**
     * Transfers the content of this file region to the specified channel.
     *
     * @param target    the destination of the transfer
     * @param position  the relative offset of the file where the transfer
     *                  begins from.  For example, <tt>0</tt> will make the
     *                  transfer start from {@link #getPosition()}th byte and
     *                  <tt>{@link #getCount()} - 1</tt> will make the last
     *                  byte of the region transferred.
     */
    long transferTo(WritableByteChannel target, long position) throws IOException;
}