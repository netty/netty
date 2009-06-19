/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
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
package org.jboss.netty.channel.socket.http;

import java.net.SocketAddress;
import java.net.URI;

/**
 * The URI of {@link HttpTunnelingServlet} where
 * {@link HttpTunnelingClientSocketChannelFactory} connects to.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class HttpTunnelAddress extends SocketAddress implements Comparable<HttpTunnelAddress> {

    private static final long serialVersionUID = -7933609652910855887L;

    private final URI uri;

    /**
     * Creates a new instance with the specified URI.
     */
    public HttpTunnelAddress(URI uri) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        this.uri = uri;
    }

    /**
     * Returns the {@link URI} where {@link HttpTunnelingServlet} is bound.
     */
    public URI getUri() {
        return uri;
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpTunnelAddress)) {
            return false;
        }

        return getUri().equals(((HttpTunnelAddress) o).getUri());
    }

    public int compareTo(HttpTunnelAddress o) {
        return getUri().compareTo(o.getUri());
    }

    @Override
    public String toString() {
        return "htun:" + getUri();
    }
}
