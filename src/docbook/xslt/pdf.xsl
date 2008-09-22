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

