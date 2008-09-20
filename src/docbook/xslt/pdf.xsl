<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:import href="classpath:/xslt/org/jboss/pdf.xsl" />
  <xsl:param name="programlisting.font" select="$monospace.font.family" />
  <xsl:attribute-set name="xref.properties">
    <xsl:attribute name="font-style">normal</xsl:attribute>
  </xsl:attribute-set>
</xsl:stylesheet>
