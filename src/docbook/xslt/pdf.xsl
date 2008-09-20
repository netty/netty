<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
                version="1.0">

  <xsl:import href="http://docbook.sourceforge.net/release/xsl/1.72.0/fo/docbook.xsl" />
  <xsl:import href="classpath:/xslt/org/jboss/pdf.xsl" />

  <!-- Override the default font settings -->
  <xsl:param name="body.font.family" select="'Times New Roman, serif'" />
  <xsl:param name="monospace.font.family" select="'DejaVu Sans Mono, monospace'" />
  <xsl:param name="sans.font.family" select="'Arial, sans-serif'" />
  <xsl:param name="title.font.family" select="$body.font.family" />
  <xsl:param name="programlisting.font" select="$monospace.font.family" />
  <xsl:param name="programlisting.font.size" select="'75%'" />

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
  <xsl:template match="//programlisting/*/text()|//programlisting/*/*/text()">
    <fo:inline font-size="80%" margin="0em" padding="0em">
      <xsl:value-of select="." />
    </fo:inline>
  </xsl:template>

  <!-- Slight baseline-shift -->
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

