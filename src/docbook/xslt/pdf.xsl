<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
                version="1.0">

  <xsl:import href="http://docbook.sourceforge.net/release/xsl/1.72.0/fo/docbook.xsl" />
  <xsl:import href="classpath:/xslt/org/jboss/pdf.xsl" />

  <xsl:param name="body.font.family" select="'Times New Roman, serif'" />
  <xsl:param name="monospace.font.family" select="'DejaVu Sans Mono, monospace'" />
  <xsl:param name="sans.font.family" select="'Arial, sans-serif'" />
  <xsl:param name="title.font.family" select="$body.font.family" />
  <xsl:param name="programlisting.font" select="$monospace.font.family" />
  <xsl:param name="programlisting.font.size" select="'75%'" />
  <xsl:param name="callout.graphics" select="1" />
  <xsl:param name="callout.unicode" select="0" />
  <xsl:param name="callout.unicode.font" select="'Malgun Gothic, sans-serif'" />

  <xsl:param name="ulink.show" select="0"/>
  <xsl:attribute-set name="xref.properties">
    <xsl:attribute name="font-style">normal</xsl:attribute>
  </xsl:attribute-set>
  <xsl:template match="//programlisting/*/text()|//programlisting/*/*/text()">
    <fo:inline font-size="75%">
      <xsl:value-of select="." />
    </fo:inline>
  </xsl:template>
</xsl:stylesheet>
