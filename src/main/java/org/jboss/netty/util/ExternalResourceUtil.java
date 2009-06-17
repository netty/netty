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

/**
 * A utility class that provides the convenient shutdown of
 * {@link ExternalResourceReleasable}s.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
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
