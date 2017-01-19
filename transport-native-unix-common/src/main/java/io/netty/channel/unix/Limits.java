/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.unix;

import static io.netty.channel.unix.LimitsStaticallyReferencedJniMethods.iovMax;
import static io.netty.channel.unix.LimitsStaticallyReferencedJniMethods.sizeOfjlong;
import static io.netty.channel.unix.LimitsStaticallyReferencedJniMethods.ssizeMax;
import static io.netty.channel.unix.LimitsStaticallyReferencedJniMethods.uioMaxIov;

public final class Limits {
    public static final int IOV_MAX = iovMax();
    public static final int UIO_MAX_IOV = uioMaxIov();
    public static final long SSIZE_MAX = ssizeMax();

    public static final int SIZEOF_JLONG = sizeOfjlong();

    private Limits() { }
}
