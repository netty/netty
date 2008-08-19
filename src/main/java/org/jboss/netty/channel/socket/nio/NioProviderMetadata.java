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
package org.jboss.netty.channel.socket.nio;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * Provides information which is specific to a NIO service provider
 * implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
class NioProviderMetadata {
    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioProviderMetadata.class);

    private static final String CONSTRAINT_LEVEL_PROPERTY =
        "java.nio.channels.spi.constraintLevel";

    /**
     * 0 - no need to wake up to get / set interestOps
     * 1 - no need to wake up to get interestOps, but need to wake up to set.
     * 2 - need to wake up to get / set interestOps
     */
    static final int CONSTRAINT_LEVEL;

    static {
        int constraintLevel = -1;

        // Use the system property if possible.
        try {
            String value = System.getProperty(CONSTRAINT_LEVEL_PROPERTY);
            constraintLevel = Integer.parseInt(value);
            if (constraintLevel < 0 || constraintLevel > 2) {
                constraintLevel = -1;
            } else {
                logger.debug(
                        "Using the specified NIO constraint level: " +
                        constraintLevel);
            }
        } catch (Exception e) {
            // format error or security issue
        }

        if (constraintLevel < 0) {
            constraintLevel = detectConstraintLevel();
            if (constraintLevel < 0) {
                constraintLevel = 2;
                logger.warn(
                        "Failed to autodetect the NIO constraint level; " +
                        "using the safest level (2)");
            } else {
                logger.debug(
                        "Using the autodected NIO constraint level: " +
                        constraintLevel);
            }
        }

        CONSTRAINT_LEVEL = constraintLevel;

        if (CONSTRAINT_LEVEL < 0 || CONSTRAINT_LEVEL > 2) {
            throw new Error(
                    "Unexpected NIO constraint level: " +
                    CONSTRAINT_LEVEL + ", please report this error.");
        }
    }

    private static int detectConstraintLevel() {
        // FIXME Auto-detect the level
        return 0;
    }
}
