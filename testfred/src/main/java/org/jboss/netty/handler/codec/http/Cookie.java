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
package org.jboss.netty.handler.codec.http;

import java.util.Set;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 */
public interface Cookie extends Comparable<Cookie> {
    String getName();
    String getValue();
    void setValue(String value);
    String getDomain();
    void setDomain(String domain);
    String getPath();
    void setPath(String path);
    String getComment();
    void setComment(String comment);
    int getMaxAge();
    void setMaxAge(int maxAge);
    int getVersion();
    void setVersion(int version);
    boolean isSecure();
    void setSecure(boolean secure);
    String getCommentUrl();
    void setCommentUrl(String commentUrl);
    boolean isDiscard();
    void setDiscard(boolean discard);
    Set<Integer> getPorts();
    void setPorts(int... ports);
    void setPorts(Iterable<Integer> ports);
}