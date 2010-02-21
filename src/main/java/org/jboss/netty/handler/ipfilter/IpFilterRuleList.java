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

import java.net.UnknownHostException;
import java.util.ArrayList;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * The Class IpFilterRuleList is a helper class to generate a List of Rules from a string.
 * In case of parse errors no exceptions are thrown. The error is logged.
 * <br>
 * Rule List Syntax:
 * <br>
 * <pre>
 * RuleList ::= Rule[,Rule]*
 * Rule ::= AllowRule | BlockRule
 * AllowRule ::= +Filter
 * BlockRule ::= -Filter
 * Filter ::= PatternFilter | CIDRFilter
 * PatternFilter ::= @see PatternRule
 * CIDRFilter ::= c:CIDRFilter
 * CIDRFilter ::= @see CIDR.newCIDR(String)
 * </pre>
 * <br>
 * Example: allow only localhost:
 * <br>
 * new IPFilterRuleHandler().addAll(new IpFilterRuleList("+n:localhost, -n:*"));
 * <br>
 * 
 * @author Ron
 */
public class IpFilterRuleList extends ArrayList<IpFilterRule>
{
   private static final long serialVersionUID = -6164162941749588780L;

   private static final InternalLogger logger = InternalLoggerFactory.getInstance(IpFilterRuleList.class);

   /**
    * Instantiates a new ip filter rule list.
    * 
    * @param rules the rules
    */
   public IpFilterRuleList(String rules)
   {
      super();
      parseRules(rules);
   }

   private void parseRules(String rules)
   {
      String[] ruless = rules.split(",");
      for (String rule : ruless)
      {
         parseRule(rule.trim());
      }
   }

   private void parseRule(String rule)
   {
      if (rule == null || rule.length() == 0)
         return;
      if (!(rule.startsWith("+") || rule.startsWith("-")))
      {
         logger.error("syntax error in ip filter rule:" + rule);
         return;
      }

      boolean allow = rule.startsWith("+");
      if (rule.charAt(1) == 'n' || rule.charAt(1) == 'i')
         this.add(new PatternRule(allow, rule.substring(1)));
      else if (rule.charAt(1) == 'c')
         try
         {
            this.add(new IpSubnetFilterRule(allow, rule.substring(3)));
         }
         catch (UnknownHostException e)
         {
            logger.error("error parsing ip filter " + rule, e);
         }
      else
         logger.error("syntax error in ip filter rule:" + rule);
   }
}
