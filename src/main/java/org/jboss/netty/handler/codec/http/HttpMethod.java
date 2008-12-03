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

/**
 * This defines the methods available via a HTTP Request.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev$, $Date$
 *
 * @apiviz.exclude
 */
public enum HttpMethod {
    /**
     * The OPTIONS method represents a request for information about the communication options available on the request/response
     * chain identified by the Request-URI. This method allows the client to determine the options and/or requirements
     * associated with a resource, or the capabilities of a server, without implying a resource action or initiating a
     * resource retrieval.
     */
    OPTIONS("OPTIONS"),

    /**
     * The GET method means retrieve whatever information (in the form of an entity) is identified by the Request-URI.
     * If the Request-URI refers to a data-producing process, it is the produced data which shall be returned as the entity
     * in the response and not the source text of the process, unless that text happens to be the output of the process.
     */
    GET("GET"),

    /**
     * The HEAD method is identical to GET except that the server MUST NOT return a message-body in the response.
     */
    HEAD("HEAD"),

    /**
     * The POST method is used to request that the origin server accept the entity enclosed in the request as a new
     * subordinate of the resource identified by the Request-URI in the Request-Line.
     */
    POST("POST"),

    /**
     * The PUT method requests that the enclosed entity be stored under the supplied Request-URI.
     */
    PUT("PUT"),

    /**
     * The DELETE method requests that the origin server delete the resource identified by the Request-URI.
     */
    DELETE("DELETE"),

    /**
     * The TRACE method is used to invoke a remote, application-layer loop- back of the request message.
     */
    TRACE("TRACE"),

    /**
     * This specification reserves the method name CONNECT for use with a proxy that can dynamically switch to being a tunnel
     */
    CONNECT("CONNECT"),;

    private String method;


    private HttpMethod(String method) {
        this.method = method;
    }

    public String getMethod() {
        return method;
    }
}
