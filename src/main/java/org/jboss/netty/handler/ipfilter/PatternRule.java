/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.netty.handler.ipfilter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * The Class PatternRule represents an IP filter rule using string patterns.
 * <br>
 * Rule Syntax:
 * <br>
 * <pre>
 * Rule ::= [n|i]:address          n stands for computer name, i for ip address
 * address ::= &lt;regex&gt; | localhost
 * regex is a regular expression with '*' as multi character and '?' as single character wild card
 * </pre>
 * <br>
 * Example: allow localhost:
 * <br>
 * new PatternRule(true, "n:localhost")
 * <br>
 * Example: allow local lan:
 * <br>
 * new PatternRule(true, "i:192.168.0.*")
 * <br>
 * Example: block all
 * <br>
 * new PatternRule(false, "n:*")
 * <br>
 * 
 * @author Ron
 */
public class PatternRule implements IpFilterRule, Comparable<Object>
{
   private static final InternalLogger logger = InternalLoggerFactory.getInstance(PatternRule.class);

   private Pattern ipPattern = null;

   private Pattern namePattern = null;

   private boolean isAllowRule = true;

   private boolean localhost = false;

   private String pattern = null;

   /**
    * Instantiates a new pattern rule.
    * 
    * @param allow indicates if this is an allow or block rule
    * @param pattern the filter pattern
    */
   public PatternRule(boolean allow, String pattern)
   {
      this.isAllowRule = allow;
      this.pattern = pattern;
      parse(pattern);
   }

   /**
    * returns the pattern.
    * 
    * @return the pattern
    */
   public String getPattern()
   {
      return this.pattern;
   }

   /* (non-Javadoc)
    * @see org.jboss.netty.handler.ipfilter.IpFilterRule#isAllowRule()
    */
   @Override
   public boolean isAllowRule()
   {
      return isAllowRule;
   }

   /* (non-Javadoc)
    * @see org.jboss.netty.handler.ipfilter.IpFilterRule#isDenyRule()
    */
   @Override
   public boolean isDenyRule()
   {
      return !isAllowRule;
   }

   /* (non-Javadoc)
    * @see org.jboss.netty.handler.ipfilter.IpSet#contains(java.net.InetAddress)
    */
   @Override
   public boolean contains(InetAddress inetAddress)
   {
      if (localhost)
         if (isLocalhost(inetAddress))
            return true;
      if (ipPattern != null)
         if (ipPattern.matcher(inetAddress.getHostAddress()).matches())
            return true;
      if (namePattern != null)
         if (namePattern.matcher(inetAddress.getHostName()).matches())
            return true;
      return false;
   }

   private void parse(String pattern)
   {
      if (pattern == null)
         return;

      String[] acls = pattern.split(",");

      String ip = "";
      String name = "";
      for (String c : acls)
      {
         c = c.trim();
         if (c.equals("n:localhost"))
            this.localhost = true;
         else if (c.startsWith("n:"))
            name = addRule(name, c.substring(2));
         else if (c.startsWith("i:"))
            ip = addRule(ip, c.substring(2));
      }
      if (ip.length() != 0)
         ipPattern = Pattern.compile(ip);
      if (name.length() != 0)
         namePattern = Pattern.compile(name);

   }

   private String addRule(String pattern, String rule)
   {
      if (rule == null || rule.length() == 0)
         return pattern;
      if (pattern.length() != 0)
         pattern += "|";
      rule = rule.replaceAll("\\.", "\\\\.");
      rule = rule.replaceAll("\\*", ".*");
      rule = rule.replaceAll("\\?", ".");
      pattern += "(" + rule + ")";
      return pattern;
   }

   private boolean isLocalhost(InetAddress address)
   {
      try
      {
         if (address.equals(InetAddress.getLocalHost()))
            return true;
      }
      catch (UnknownHostException e)
      {
         logger.info("error getting ip of localhost", e);
      }
      try
      {
         InetAddress[] addrs = InetAddress.getAllByName("127.0.0.1");
         for (InetAddress addr : addrs)
            if (addr.equals(address))
               return true;
      }
      catch (UnknownHostException e)
      {
         logger.info("error getting ip of localhost", e);
      }
      return false;

   }

   /* (non-Javadoc)
    * @see java.lang.Comparable#compareTo(java.lang.Object)
    */
   @Override
   public int compareTo(Object o)
   {
      if (o == null)
         return -1;
      if (!(o instanceof PatternRule))
         return -1;
      PatternRule p = (PatternRule) o;
      if (p.isAllowRule() && !this.isAllowRule)
         return -1;
      if (this.pattern == null && p.pattern == null)
         return 0;
      if (this.pattern != null)
         return this.pattern.compareTo(p.getPattern());
      return -1;
   }

}
