<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xsl:output indent="yes"
              method="xml"
              omit-xml-declaration="yes"
              />
  <xsl:strip-space elements="*"/>

  <xsl:param name="myxsd" select="''"/>
  <xsl:variable name="xsd" select="document(concat('data:text/xml,',$myxsd))"/>

  <xsl:variable name="input">
    <xsl:copy-of select="/"/>
  </xsl:variable>

  <xsl:template match="/*">
    <xsl:variable name="firstContext" select="name()"/>
    <xsl:variable name="xsdElems" select="$xsd/xs:schema/xs:element[@name=$firstContext]/xs:complexType/xs:sequence/xs:element/@name"/>
    <xsl:element name="{$firstContext}">
      <xsl:for-each select="$xsdElems">
        <xsl:variable name="secondContext" select="."/>
        <xsl:element name="{$secondContext}">
          <xsl:value-of select="$input/*/*[@name=$secondContext]/@value"/>
        </xsl:element>
      </xsl:for-each>
    </xsl:element>
  </xsl:template>
</xsl:stylesheet>
