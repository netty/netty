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
package org.jboss.netty.util.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.util.DebugUtil;

/**
 * Simplifies an exception stack trace by removing unnecessary
 * {@link StackTraceElement}s.  Please note that the stack trace simplification
 * is disabled if {@linkplain DebugUtil debug mode} is turned on.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class StackTraceSimplifier {

    private static final boolean SIMPLIFY_STACK_TRACE = !DebugUtil.isDebugEnabled();
    private static final Pattern EXCLUDED_STACK_TRACE =
        Pattern.compile(
                "^org\\.jboss\\.netty\\." +
                "(util\\.internal\\.(ThreadRenamingRunnable)" +
                "|channel\\.(SimpleChannelHandler|DefaultChannelPipeline.*))$");

    /**
     * Removes unnecessary {@link StackTraceElement}s from the specified
     * exception. {@link ThreadRenamingRunnable}, {@link SimpleChannelHandler},
     * and {@link DefaultChannelPipeline} will be dropped from the trace.
     */
    public static void simplify(Throwable e) {
        if (!SIMPLIFY_STACK_TRACE) {
            return;
        }

        if (e.getCause() != null) {
            simplify(e.getCause());
        }

        StackTraceElement[] trace = e.getStackTrace();
        if (trace == null || trace.length == 0) {
            return;
        }

        // Perhaps Netty bug.  Let us not strip things out.
        if (EXCLUDED_STACK_TRACE.matcher(trace[0].getClassName()).matches()) {
            return;
        }

        List<StackTraceElement> simpleTrace =
            new ArrayList<StackTraceElement>(trace.length);

        simpleTrace.add(trace[0]);

        // Remove unnecessary stack trace elements.
        for (int i = 1; i < trace.length; i ++) {
            if (EXCLUDED_STACK_TRACE.matcher(trace[i].getClassName()).matches()) {
                continue;
            }
            simpleTrace.add(trace[i]);
        }

        e.setStackTrace(
                simpleTrace.toArray(new StackTraceElement[simpleTrace.size()]));
    }
}
