/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.handler.codec.http;

import java.util.Set;

/**
 * An HTTP <a href="http://en.wikipedia.org/wiki/HTTP_cookie">Cookie</a>.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 */
public interface Cookie extends Comparable<Cookie> {

    /**
     * Returns the name of this cookie.
     */
    String getName();

    /**
     * Returns the value of this cookie.
     */
    String getValue();

    /**
     * Sets the value of this cookie.
     */
    void setValue(String value);

    /**
     * Returns the domain of this cookie.
     */
    String getDomain();

    /**
     * Sets the domain of this cookie.
     */
    void setDomain(String domain);

    /**
     * Returns the path of this cookie.
     */
    String getPath();

    /**
     * Sets the path of this cookie.
     */
    void setPath(String path);

    /**
     * Returns the comment of this cookie.
     */
    String getComment();

    /**
     * Sets the comment of this cookie.
     */
    void setComment(String comment);

    /**
     * Returns the max age of this cookie in seconds.
     */
    int getMaxAge();

    /**
     * Sets the max age of this cookie in seconds.  If {@code 0} is specified,
     * this cookie will be removed by browser because it will be expired
     * immediately.  If {@code -1} is specified, this cookie will be removed
     * when a user terminates browser.
     */
    void setMaxAge(int maxAge);

    /**
     * Returns the version of this cookie.
     */
    int getVersion();

    /**
     * Sets the version of this cookie.
     */
    void setVersion(int version);

    /**
     * Returns the secure flag of this cookie.
     */
    boolean isSecure();

    /**
     * Sets the secure flag of this cookie.
     */
    void setSecure(boolean secure);

    /**
     * Returns the comment URL of this cookie.
     */
    String getCommentUrl();

    /**
     * Sets the comment URL of this cookie.
     */
    void setCommentUrl(String commentUrl);

    /**
     * Returns the discard flag of this cookie.
     */
    boolean isDiscard();

    /**
     * Sets the discard flag of this cookie.
     */
    void setDiscard(boolean discard);

    /**
     * Returns the ports of this cookie.
     */
    Set<Integer> getPorts();

    /**
     * Sets the ports of this cookie.
     */
    void setPorts(int... ports);

    /**
     * Sets the ports of this cookie.
     */
    void setPorts(Iterable<Integer> ports);
}