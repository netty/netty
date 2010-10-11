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
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
                version="1.0">

  <xsl:import href="classpath:/xslt/org/jboss/pdf.xsl" />
  <xsl:import href="classpath:/xslt/org/jboss/xslt/fonts/pdf/fonts.xsl" />

  <!-- Override the default font settings -->
  <xsl:template name="pickfont-serif">
    <xsl:variable name="font">
      <xsl:call-template name="pickfont"/>
    </xsl:variable>
    <xsl:copy-of select="$font"/>
    <xsl:text>Liberation Serif,serif</xsl:text>
  </xsl:template>
  <xsl:param name="title.font.family">
    <xsl:variable name="font">
      <xsl:call-template name="pickfont-serif"/>
    </xsl:variable>
    <xsl:message>
      <xsl:text>Setting 'title.font.family' param=</xsl:text><xsl:copy-of select="$font"/>
    </xsl:message>
    <xsl:copy-of select="$font"/>
  </xsl:param>
  <xsl:param name="body.font.family">
    <xsl:variable name="font">
      <xsl:call-template name="pickfont-serif"/>
    </xsl:variable>
    <xsl:message>
      <xsl:text>Setting 'body.font.family' param=</xsl:text><xsl:copy-of select="$font"/>
    </xsl:message>
    <xsl:copy-of select="$font"/>
  </xsl:param>
  <xsl:param name="monospace.font.family">
    <xsl:variable name="font">
      <xsl:call-template name="pickfont-mono"/>
    </xsl:variable>
    <xsl:message>
      <xsl:text>Setting 'monospace.font.family' param=</xsl:text><xsl:copy-of select="$font"/>
    </xsl:message>
    <xsl:copy-of select="$font"/>
  </xsl:param>
  <xsl:param name="sans.font.family">
    <xsl:variable name="font">
      <xsl:call-template name="pickfont-sans"/>
    </xsl:variable>
    <xsl:message>
      <xsl:text>Setting 'sans.font.family' param=</xsl:text><xsl:copy-of select="$font"/>
    </xsl:message>
    <xsl:copy-of select="$font"/>
  </xsl:param>
  <xsl:param name="programlisting.font">
    <xsl:variable name="font">
      <xsl:call-template name="pickfont-mono"/>
    </xsl:variable>
    <xsl:message>
      <xsl:text>Setting 'programlisting.font' param=</xsl:text><xsl:copy-of select="$font"/>
    </xsl:message>
    <xsl:copy-of select="$font"/>
  </xsl:param>
  <xsl:param name="programlisting.font.size" select="'85%'" />

  <!-- Remove the blank pages between the chapters -->
  <xsl:param name="double.sided" select="0" />

  <!-- Use SVG for callout images instead of PNG -->
  <xsl:param name="callout.graphics" select="1" />
  <xsl:param name="callout.graphics.extension" select="'.svg'" />

  <!-- Hide URL -->
  <xsl:param name="ulink.show" select="0"/>

  <!-- Don't use italic font for links -->
  <xsl:attribute-set name="xref.properties">
    <xsl:attribute name="font-style">normal</xsl:attribute>
  </xsl:attribute-set>

  <!-- Decrease the link font size in the program listing -->
  <xsl:attribute-set name="monospace.properties">
    <xsl:attribute name="font-size">1em</xsl:attribute>
    <xsl:attribute name="font-family">
        <xsl:value-of select="$monospace.font.family"/>
    </xsl:attribute>
  </xsl:attribute-set>
  
  <!-- Add some spacing between callout listing items -->
  <xsl:template match="callout">
    <xsl:variable name="id"><xsl:call-template name="object.id"/></xsl:variable>
    <fo:list-item id="{$id}" space-before="1em">
      <fo:list-item-label end-indent="label-end()">
        <fo:block>
          <xsl:call-template name="callout.arearefs">
            <xsl:with-param name="arearefs" select="@arearefs"/>
          </xsl:call-template>
        </fo:block>
      </fo:list-item-label>
      <fo:list-item-body start-indent="body-start()">
        <fo:block padding-top="0.2em">
          <xsl:apply-templates/>
        </fo:block>
      </fo:list-item-body>
    </fo:list-item>
  </xsl:template>
  
  <!-- Slight baseline-shift for callouts in the program listing -->
  <xsl:template name="callout-bug">
    <xsl:param name="conum" select='1'/>
    <xsl:choose>
      <xsl:when test="$conum &lt;= $callout.graphics.number.limit">
        <xsl:variable name="filename"
                      select="concat($callout.graphics.path, $conum,
                                     $callout.graphics.extension)"/>

        <fo:external-graphic content-width="{$callout.icon.size}"
                             width="{$callout.icon.size}"
                             padding="0.0em" margin="0.0em"
                             baseline-shift="-0.375em">
          <xsl:attribute name="src">
            <xsl:choose>
              <xsl:when test="$passivetex.extensions != 0
                              or $fop.extensions != 0
                              or $arbortext.extensions != 0">
                <xsl:value-of select="$filename"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:text>url(</xsl:text>
                <xsl:value-of select="$filename"/>
                <xsl:text>)</xsl:text>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:attribute>
        </fo:external-graphic>
      </xsl:when>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>

