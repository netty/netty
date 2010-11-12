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

/**
 * Overrides the thread name proposed by {@link ThreadRenamingRunnable}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev$, $Date$
 */
public interface ThreadNameDeterminer {

    /**
     * The default {@link ThreadNameDeterminer} that generates a thread name
     * which contains all specified information.
     */
    ThreadNameDeterminer PROPOSED = new ThreadNameDeterminer() {
        @Override
        public String determineThreadName(String current, String service,
                String category, String parentId, String id, String comment) throws Exception {

            String newName =
                (format("",  " ", service) +
                 format("",  " ", category) +
                 format("#", " ", parentId, id) +
                 format("(", ")", comment)).trim();
            if (newName.length() == 0) {
                return null;
            } else {
                return newName;
            }
        }

        private String format(String prefix, String postfix, String... components) {
            StringBuilder buf = new StringBuilder();
            for (String c: components) {
                if (c.length() == 0) {
                    continue;
                }
                buf.append(c);
                buf.append(':');
            }

            if (buf.length() == 0) {
                return "";
            }

            buf.setLength(buf.length() - 1); // Remove trailing ':'
            return prefix + buf + postfix;
        }
    };

    /**
     * An alternative {@link ThreadNameDeterminer} that rejects the proposed
     * thread name and retains the current one.
     */
    ThreadNameDeterminer CURRENT = new ThreadNameDeterminer() {
        @Override
        public String determineThreadName(String current, String service,
                String category, String parentId, String id, String comment) throws Exception {
            return null;
        }
    };

    /**
     * Overrides the thread name proposed by {@link ThreadRenamingRunnable}.
     *
     * @param current   the current thread name
     * @param service   the service name (e.g. <tt>"NewIO"</tt> or <tt>"OldIO"</tt>)
     * @param category  the category name (e.g. <tt>"ServerBoss"</tt> or <tt>"ClientWorker"</tt>)
     * @param parentId  the parent thread ID (e.g. <tt>"1"</tt>)
     * @param id        the thread ID (e.g. <tt>"3"</tt>)
     * @param comment   the optional comment which might help debugging
     *
     * @return the actual new thread name.
     *         If {@code null} is returned, the proposed thread name is
     *         discarded (i.e. no rename).
     */
    String determineThreadName(
            String current,
            String service, String category, String parentId, String id, String comment) throws Exception;
}
