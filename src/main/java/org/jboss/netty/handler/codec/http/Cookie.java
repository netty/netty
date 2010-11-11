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
package org.jboss.netty.handler.codec.http;

import java.util.Set;

/**
 * An HTTP <a href="http://en.wikipedia.org/wiki/HTTP_cookie">Cookie</a>.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev: 2241 $, $Date: 2010-04-16 13:12:43 +0900 (Fri, 16 Apr 2010) $
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
     * Returns if this cookie cannot be accessed through client side script.
     * This flag works only if the browser supports it.  For more information,
     * see <a href="http://www.owasp.org/index.php/HTTPOnly">here</a>.
     */
    boolean isHttpOnly();

    /**
     * Sets if this cookie cannot be accessed through client side script.
     * This flag works only if the browser supports it.  For more information,
     * see <a href="http://www.owasp.org/index.php/HTTPOnly">here</a>.
     */
    void setHttpOnly(boolean httpOnly);

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
