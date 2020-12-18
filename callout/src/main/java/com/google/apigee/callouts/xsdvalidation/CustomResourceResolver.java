package com.google.apigee.callouts.xsdvalidation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

public class CustomResourceResolver implements LSResourceResolver {
  private final Map<String, String> knownResources;
  private final Function<String,String> httpRefResolver;

  @SuppressWarnings("unchecked")
  public CustomResourceResolver(Map<String, String> map,
                                Function<String,String> httpRefResolver) {
    super();
    this.knownResources = map;
    this.httpRefResolver = httpRefResolver;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.w3c.dom.ls.LSResourceResolver#resolveResource(java.lang.String,
   * java.lang.String, java.lang.String, java.lang.String, java.lang.String)
   */
  public LSInput resolveResource(
      String type, String namespaceURI, String publicId, final String systemId, String baseURI) {

    // System.out.printf("\n** resolve resource: namespaceURI(%s) publicId(%s) systemId(%s) baseuri(%s)\n",
    //                   namespaceURI,
    //                   publicId,
    //                   systemId,
    //                   baseURI
    //                   );

    return new LSInput() {
      public String getBaseURI() {
        return null;
      }

      public InputStream getByteStream() {
        if (systemId.startsWith("http"))
          return new ByteArrayInputStream(httpRefResolver.apply(systemId)
                                          .getBytes(StandardCharsets.UTF_8));

        if (knownResources != null && knownResources.containsKey(systemId))
          return new ByteArrayInputStream(
              knownResources.get(systemId).getBytes(StandardCharsets.UTF_8));


        return null;
      }

      public boolean getCertifiedText() {
        return false;
      }

      public Reader getCharacterStream() {
        return null;
      }

      public String getEncoding() {
        return null;
      }

      public String getPublicId() {
        return null;
      }

      public String getStringData() {
        return null;
      }

      public String getSystemId() {
        return null;
      }

      public void setBaseURI(String baseURI) {}

      public void setByteStream(InputStream byteStream) {}

      public void setCertifiedText(boolean certifiedText) {}

      public void setCharacterStream(Reader characterStream) {}

      public void setEncoding(String encoding) {}

      public void setPublicId(String publicId) {}

      public void setStringData(String stringData) {}

      public void setSystemId(String systemId) {}
    };
  }
}
