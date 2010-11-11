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
package org.jboss.netty.channel.local;

import java.net.SocketAddress;

/**
 * An endpoint in the local transport.  Each endpoint is identified by a unique
 * case-insensitive string, except for the pre-defined value called
 * {@code "ephemeral"}.
 *
 * <h3>Ephemeral Address</h3>
 *
 * An ephemeral address is an anonymous address which is assigned temporarily
 * and is released as soon as the connection is closed.  All ephemeral addresses
 * have the same ID, {@code "ephemeral"}, but they are not equal to each other.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2241 $, $Date: 2010-04-16 13:12:43 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.landmark
 */
public final class LocalAddress extends SocketAddress implements Comparable<LocalAddress> {

    private static final long serialVersionUID = -3601961747680808645L;

    public static final String EPHEMERAL = "ephemeral";

    private final String id;
    private final boolean ephemeral;

    /**
     * Creates a new instance with the specified ID.
     */
    public LocalAddress(int id) {
        this(String.valueOf(id));
    }

    /**
     * Creates a new instance with the specified ID.
     */
    public LocalAddress(String id) {
        if (id == null) {
            throw new NullPointerException("id");
        }
        id = id.trim().toLowerCase();
        if (id.length() == 0) {
            throw new IllegalArgumentException("empty id");
        }
        this.id = id;
        ephemeral = id.equals("ephemeral");
    }

    /**
     * Returns the ID of this address.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns {@code true} if and only if this address is ephemeral.
     */
    public boolean isEphemeral() {
        return ephemeral;
    }

    @Override
    public int hashCode() {
        if (ephemeral) {
            return System.identityHashCode(this);
        } else {
            return id.hashCode();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocalAddress)) {
            return false;
        }

        if (ephemeral) {
            return this == o;
        } else {
            return getId().equals(((LocalAddress) o).getId());
        }
    }

    // FIXME: This comparison is broken!  Assign distinct port numbers for
    //        ephemeral ports, just like O/S does for port number 0.  It will
    //        break backward compatibility though.

    public int compareTo(LocalAddress o) {
        if (ephemeral) {
            if (o.ephemeral) {
                if (this == o){
                    return 0;
                }

                int a = System.identityHashCode(this);
                int b = System.identityHashCode(this);
                if (a < b) {
                    return -1;
                } else if (a > b) {
                    return 1;
                } else {
                    throw new Error(
                            "Two different ephemeral addresses have " +
                            "same identityHashCode.");
                }
            } else {
                return 1;
            }
        } else {
            if (o.ephemeral) {
                return -1;
            } else {
                return getId().compareTo(o.getId());
            }
        }
    }

    @Override
    public String toString() {
        return "local:" + getId();
    }
}
