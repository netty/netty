/*
 * Copyright 2009 Red Hat, Inc.
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
package org.jboss.netty.util;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.util.internal.SystemPropertyUtil;

/**
 * Determines if Netty is running in a debug mode or not.  Please note that
 * this is not a Java debug mode.  You can enable Netty debug mode by
 * specifying the {@code "org.jboss.netty.debug"} system property (e.g.
 * {@code java -Dorg.jboss.netty.debug ...})
 * <p>
 * If debug mode is disabled (default), the stack trace of the exceptions are
 * compressed to help debugging a user application.
 * <p>
 * If debug mode is enabled, the stack trace of the exceptions raised in
 * {@link ChannelPipeline} or {@link ChannelSink} are retained as it is to help
 * debugging Netty.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class DebugUtil {

    /**
     * Returns {@code true} if and only if Netty debug mode is enabled.
     */
    public static boolean isDebugEnabled() {
        String value;
        try {
            value = SystemPropertyUtil.get("org.jboss.netty.debug");
        } catch (Exception e) {
            value = null;
        }

        if (value == null) {
            return false;
        }

        value = value.trim().toUpperCase();
        return !value.startsWith("N") &&
               !value.startsWith("F") &&
               !value.equals("0");
    }

    private DebugUtil() {
        // Unused
    }
}
