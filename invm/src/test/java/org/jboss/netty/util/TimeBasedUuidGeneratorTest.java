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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class TimeBasedUuidGeneratorTest {
    private static final int COUNT = 16384 * 8;

    @Test
    public void shouldGenerateTimeBasedUuid() {
        UUID uuid = TimeBasedUuidGenerator.generate();
        assertEquals(1, uuid.version());
        assertEquals(2, uuid.variant());
    }

    @Test
    public void shouldNotDuplicate() {
        List<UUID> uuids = new ArrayList<UUID>(COUNT);
        for (int i = 0; i < COUNT; i ++) {
            uuids.add(TimeBasedUuidGenerator.generate());
        }

        Collections.sort(uuids);

        for (int i = 1; i < COUNT; i ++) {
            UUID a = uuids.get(i - 1);
            UUID b = uuids.get(i);
            assertFalse("Duplicate UUID: " + a.toString(), a.equals(b));
        }
    }
}
