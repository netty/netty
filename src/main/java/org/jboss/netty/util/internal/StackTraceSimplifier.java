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
package org.jboss.netty.util.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.StaticChannelPipeline;
import org.jboss.netty.util.DebugUtil;
import org.jboss.netty.util.ThreadRenamingRunnable;

/**
 * Simplifies an exception stack trace by removing unnecessary
 * {@link StackTraceElement}s.  Please note that the stack trace simplification
 * is disabled if {@linkplain DebugUtil debug mode} is turned on.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class StackTraceSimplifier {

    private static final boolean SIMPLIFY_STACK_TRACE = !DebugUtil.isDebugEnabled();
    private static final Pattern EXCLUDED_STACK_TRACE =
        Pattern.compile(
                "^org\\.jboss\\.netty\\." +
                "(util\\.(ThreadRenamingRunnable|internal\\.DeadLockProofWorker)" +
                "|channel\\.(SimpleChannel(Upstream|Downstream)?Handler|(Default|Static)ChannelPipeline.*))(\\$.*)?$");

    /**
     * Removes unnecessary {@link StackTraceElement}s from the specified
     * exception. {@link ThreadRenamingRunnable}, {@link SimpleChannelHandler},
     * {@link DefaultChannelPipeline}, and {@link StaticChannelPipeline}
     * will be dropped from the trace.
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
