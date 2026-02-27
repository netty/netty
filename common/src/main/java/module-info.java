/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
module io.netty5.common {
    requires jdk.unsupported;
    requires java.logging;
    requires static org.jctools.core;
    requires static jdk.jfr;
    requires static org.apache.commons.logging;
    requires static org.apache.log4j;
    requires static org.apache.logging.log4j;
    requires static org.jetbrains.annotations;
    requires static org.slf4j;
    requires static org.graalvm.nativeimage;
    requires static reactor.blockhound;

    exports io.netty.util;
    exports io.netty.util.collection;
    exports io.netty.util.concurrent;

    exports io.netty.util.internal to
            io.netty5.buffer;
    exports io.netty.util.internal.logging to
            io.netty5.buffer;
}