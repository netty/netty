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

import java.util.Map;
import java.util.HashMap;

/**
 * The response codes to returnm in the response. see http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public enum HttpResponseStatusCode
{
   //todo - add the rest of the responses as we need them

   OK(200, ""),

   INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
   ;

   private static Map<Integer, HttpResponseStatusCode> RESPONSE_MAP = new HashMap<Integer, HttpResponseStatusCode>();
   static
   {
      RESPONSE_MAP.put(OK.code, OK);
      RESPONSE_MAP.put(INTERNAL_SERVER_ERROR.code, INTERNAL_SERVER_ERROR);
   }

   public static HttpResponseStatusCode getResponseStatusCode(int id)
   {
      HttpResponseStatusCode code = RESPONSE_MAP.get(id);
      if(code == null)
      {
         throw new IllegalArgumentException("Invalid Response Status");
      }
      return code;
   }

   private int code;
   private String description;

   private HttpResponseStatusCode(int code, String description)
   {
      this.code = code;
      this.description = description;
   }

   public int getCode()
   {
      return code;
   }

   public String getDescription()
   {
      return description;
   }
}
