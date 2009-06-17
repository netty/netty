/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
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
