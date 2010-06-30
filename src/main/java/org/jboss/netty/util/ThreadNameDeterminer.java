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
        public String determineThreadName(String current, String service,
                String category, String id, String comment) throws Exception {

            String newName =
                (format("",  service,  " ") +
                 format("",  category, " ") +
                 format("#", id,       " ") +
                 format("(", comment,  ")")).trim();
            if (newName.length() == 0) {
                return null;
            } else {
                return newName;
            }
        }

        private String format(String prefix, String s, String postfix) {
            if (s.length() == 0) {
                return "";
            } else {
                return prefix + s + postfix;
            }
        }
    };

    /**
     * An alternative {@link ThreadNameDeterminer} that rejects the proposed
     * thread name and retains the current one.
     */
    ThreadNameDeterminer CURRENT = new ThreadNameDeterminer() {
        public String determineThreadName(String current, String service,
                String category, String id, String comment) throws Exception {
            return null;
        }
    };

    /**
     * Overrides the thread name proposed by {@link ThreadRenamingRunnable}.
     *
     * @param current   the current thread name
     * @param service   the service name (e.g. <tt>"New I/O"</tt>)
     * @param category  the category name (e.g. <tt>"server boss"</tt>)
     * @param id        the thread ID (e.g. <tt>"3"</tt> or <tt>"1-3"</tt>)
     * @param comment   the optional comment which might help debugging
     *
     * @return the actual new thread name.
     *         If {@code null} is returned, the proposed thread name is
     *         discarded (i.e. no rename).
     */
    String determineThreadName(
            String current,
            String service, String category, String id, String comment) throws Exception;
}
