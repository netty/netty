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
 * A utility class that provides the convenient shutdown of
 * {@link ExternalResourceReleasable}s.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class ExternalResourceUtil {

    /**
     * Releases the specified {@link ExternalResourceReleasable}s.
     */
    public static void release(ExternalResourceReleasable... releasables) {
        ExternalResourceReleasable[] releasablesCopy =
            new ExternalResourceReleasable[releasables.length];

        for (int i = 0; i < releasables.length; i ++) {
            if (releasables[i] == null) {
                throw new NullPointerException("releasables[" + i + "]");
            }
            releasablesCopy[i] = releasables[i];
        }

        for (ExternalResourceReleasable e: releasablesCopy) {
            e.releaseExternalResources();
        }
    }

    private ExternalResourceUtil() {
        super();
    }
}
