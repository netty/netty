<?xml version="1.0" encoding="UTF-8"?>
<!--
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
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:import href="classpath:/xslt/org/jboss/xhtml.xsl" />

  <xsl:param name="siteHref" select="'http://www.jboss.org/netty/'"/>
  <xsl:param name="docHref" select="'http://www.jboss.org/netty/documentation.html'"/>
  <xsl:param name="siteLinkText" select="'JBoss.org: Netty - The Client Server Framework and Tools'"/>

  <xsl:param name="callout.defaultcolumn">1</xsl:param>
</xsl:stylesheet>
