# Java callout for XSD Validation

This directory contains the Java source code required to compile a Java callout
for Apigee that does Validation of an XML document against an XSD. There's
a built-in policy that does this; this callout is different in that it is a bit
more flexible, in these ways:

* The person configuring the policy can specify the XSD in a context variable.
  This is nice because it means the XSD can be dynamically determined or loaded
  at runtime.

* It is possible to specify an XSD source available at an external HTTP endpoint.

* You can use a schema that uses xs:include or xs:import of other schema.

* You can configure the policy to require a particular root element.

* The error messages that get emitted are more verbose and informative. This
  helps people diagnose runtime problems, or provide feedback to API callers.


## Disclaimer

This example is not an official Google product, nor is it part of an official Google product.

## Using this policy

You do not need to build the source code in order to use the policy in Apigee
Edge. All you need is the built JAR, and the appropriate configuration for
the policy.  If you want to build it, feel free.  The instructions are at the
bottom of this readme.


1. copy the jar file, available in target/apigee-custom-xsd-validation-20211021.jar , if you have built
   the jar, or in [the repo](bundle/apiproxy/resources/java/apigee-custom-xsd-validation-20211021.jar)
   if you have not, to your apiproxy/resources/java directory. Also copy all the required
   dependencies. (See below) You can do this offline, or using the graphical Proxy Editor in the
   Apigee Edge Admin Portal.

2. include a Java callout policy in your
   apiproxy/resources/policies directory. It should look
   like this:
   ```xml
    <JavaCallout name='Java-Xsd'>
      <Properties>
        <Property name='schema'>....</Property>
           ....
      </Properties>
      <ClassName>com.google.apigee.callouts.xsdvalidation.XsdValidatorCallout</ClassName>
      <ResourceURL>java://apigee-custom-xsd-validation-20211021.jar</ResourceURL>
    </JavaCallout>
   ```

5. use the Edge UI, or a command-line tool like
   [importAndDeploy.js](https://github.com/DinoChiesa/apigee-edge-js-examples/blob/master/importAndDeploy.js)
   or Powershell's [Import-EdgeApi](https://github.com/DinoChiesa/Edge-Powershell-Admin/blob/develop/PSApigeeEdge/Public/Import-EdgeApi.ps1)
   or similar to import the proxy into an Edge organization. If you need to, make sure to deploy the proxy.

6. use a client (like curl, or Postman, or Powershell's Invoke-WebRequest) to generate and send http requests to tickle the proxy.


## Notes

There is one callout class, com.google.apigee.callouts.xsdvalidation.XsdValidatorCallout,
which performs the XSD validation.

You must configure the callout with Property elements in the policy configuration.

| property name        | description        |
---------------------- | ------------------ |
| schema               |  required. the main XSD to use for validation. |
| schema:xxxx          |  optional. any dependent schema. Replace xxxx with the value of the schemaLocation in the main XSD. |
| source               |  optional. the string or message to use to obtain the XML to validate. Defaults to "message.content" |
| use-dom-source       |  optional. true/false. Default: false. When this is false, the callout cannot emit the path of the failing XML element, but it uses less memory at runtime. I recommend you set this as true during development, and consider setting it to true in production. |
| required-root        |  optional. The localname of the root element that you'd like to require. Simply validating with XSD, does not check that the root element is a particular element.  This property allows you to tell the callout to perform that extra check.  |
| required-root-namepsace |  optional, but required if `required-root` is present. The namespace URI of the root element that you'd like to require. |


Examples follow.

To use this callout, you will need an API Proxy, of course.

## Example 1: Perform a simple validation

```xml
<JavaCallout name='JavaCallout-XSD-1'>
  <Properties>
     <Property name='schema'>{xsdurl}</Property>
     <Property name='source'>request</Property>
  </Properties>
  <ClassName>com.google.apigee.callouts.xsdvalidation.XsdValidatorCallout</ClassName>
  <ResourceURL>java://apigee-custom-xsd-validation-20211021.jar</ResourceURL>
</JavaCallout>
```

The xsd property specifies the schema. This can be one of 4 forms:

* a file reference, like file://filename.xsd
* a url beginning with http:// or https://
* a UTF-8 string that, when trimmed, defines an XML Schema. (It begins with `<schema>` and ends with `</schema>`) In other words, you can directly embed the XSD into the configuration for the policy.
* a string enclosed in curly-braces, indicating a variable which resolves to one of the above.

Note: you cannot specify an XSD which is uploaded as a resource to the proxy, or the environment, or the organization. You cannot use an xsd:// url.


If using a file reference (file://something.xsd), the file must be present as a resource in the JAR file. This requires you to re-build and
re-package the jar file. Place your .xsd file into the callout/src/main/resources directory.

The structure of the generated jar must be like so:

```
meta-inf/
meta-inf/manifest.mf
com/
com/google/
com/google/apigee/
com/google/apigee/callouts/
com/google/apigee/callouts/xsdvalidation
com/google/apigee/callouts/xsdvalidation/XsdValidatorCallout.class
resources/
resources/filename.xsd
```

You can have as many XSDs in the resources directory as you like.

If a URL, the URL must return a valid XSD. The URL should be accessible from the message
processor. The contents of the URL will be cached, currently for 10 minutes. This cache period is
not confgurable, but you could change it in the source and re-compile if you like.


In the case that you have multiple XSDs, in which one XSD imports another, then you need to specify all of them explicitly.
Specify them in the xsd property, separated by commas, like this:

```xml
<JavaCallout name='JavaCallout-XSD-2'>
  <Properties>
     <Property name='schema'>{xsdurl1}</Property>
     <Property name='schema:child-schema1.xsd'>{childxsdurl1}</Property>
     <Property name='schema:child-schema2.xsd'>https://foo/bar/bam/schemaurl2.xsd</Property>
     <Property name='source'>request</Property>
  </Properties>
  <ClassName>com.google.apigee.callouts.xsdvalidation.XsdValidatorCallout</ClassName>
  <ResourceURL>java://apigee-custom-xsd-validation-20211021.jar</ResourceURL>
</JavaCallout>
```

The `schema` property defines the main XSD.  This XSD imports or includes two
other schema, both of which use a `schemaLocation` attribute. The property
`schema:child-schema1.xsd` specifies a curly-braced variable which at runtime
will hold the URL that returns the schema noted in the main XSD with
`schemaLocation` of `child-schema1.xsd`. The property `schema:child-schema2.xsd`
specifies a hard-coded URL that will return the schema noted with
`schemaLocation` of `child-schema2.xsd`.

The source property specifies where to find the XML to be validated. This must be a variable name.
Do not use curly-braces. If this variable resolves to a Message type (such as request or response,
or a message created using AssignMessage, or a response obtained from a ServiceCallout), then the
callout will validate the content of that Message variable. If no source is specified, then the
policy will default to using the value of context variable 'message.content' for the source.

The policy does not have the capability to retrieve the source XML from a URL, or from a file. In most cases the source XML will be a request or response.


The policy sets these context variables as output:

| variable name            | description                                                    |
-------------------------- | -------------------------------------------------------------- |
| xsd\_valid                | result of the validation check. It will hold "true" if the document is valid against the schema; "false" if not. If the XML is not well-formed, then the value will get "false".
| xsd\_validation_exceptions| a string, containing a list of 1 or more messages, each separated by a newline, indicating what makes the document invalid. If the document his valid, this variable will be null. This could be suitable for sending back to the caller.
| xsd\_error                | set if the policy failed. This is usually the result of a configuration error. Processing an invalid document will not be a failure. The policy succeeds though the document is deemed invalid.
| xsd\_exception            | a diagnostic message indicating what caused the policy to fail at runtime. Set only if xsd_error is set.
| xsd\_failing\_paths        | a list of paths to the elements in the document that caused the failure. Set only when a failure occurs and when `use-dom-source` is true. |


Here's an example of the list of messages emitted in xsd\_validation_exceptions when a not-well-formed XML document is validated against a schema for "puchaseOrder":

```
1. org.xml.sax.SAXParseException; lineNumber: 1; columnNumber: 83; cvc-elt.1: Cannot find the declaration of element 'purchaseOrder'.
2. org.xml.sax.SAXParseException; lineNumber: 10; columnNumber: 10; The value of attribute "country" associated with an element type "billTo" must not contain the '<' character.
```

The list of messages is limited to 10.


To get the failing element, set the `use-dom-source` property to "true":

```xml
<JavaCallout name='JavaCallout-XSD-3'>
  <Properties>
     <Property name='schema'>{xsdurl1},{xsdurl2},https://foo/bar/bam/schemaurl.xsd</Property>
     <Property name='source'>request</Property>
     <Property name='use-dom-source'>true</Property>
  </Properties>
  <ClassName>com.google.apigee.callouts.xsdvalidation.XsdValidatorCallout</ClassName>
  <ResourceURL>java://apigee-custom-xsd-validation-20211021.jar</ResourceURL>
</JavaCallout>
```

...and then, in the proxy logic, check the context variable `xsd_failing_paths` for the path to the element.
Some notes:

* A failing path looks like: "#document/purchaseOrder/billTo/state". These are
  not xpaths, but rather labels intended to help with human diagnostics.
  (There is not a general way to determine a valid xpath from an element.)
* If there is more than one path, they will be separated by commas.
* Using the `use-dom-source` will consume more memory per request. It is not recommended for high-scale use with large documents.


## Sample Proxy

There is [a sample API Proxy](./bundle) included in this repo.  It verifies an inbound message against an XSD that is assigned in an AssignedMessage policy.
Deploy it to your org + environment, and test it like this:

Success case; document is valid per schema:
```
ORG=myorg
ENV-myenv
curl -i -H content-type:application/xml \
  https://$ORG-$ENV.apigee.net/java-xsd/t1 \
  -d '<purchaseOrder xmlns="http://tempuri.org/po.xsd" orderDate="2018-07-09">
    <shipTo country="US">
        <name>Alice Smith</name>
        <street>123 Maple Street</street>
        <city>Mill Valley</city>
        <state>CA</state>
        <zip>90952</zip>
    </shipTo>
    <billTo country="US">
        <name>Robert Smith</name>
        <street>8 Oak Avenue</street>
        <city>Old Town</city>
        <state>PA</state>
        <zip>95819</zip>
    </billTo>
    <comment>Hurry, my lawn is going wild!</comment>
    <items>
        <item partNum="872-AA">
            <productName>Lawnmower</productName>
            <quantity>1</quantity>
            <USPrice>148.95</USPrice>
            <comment>Confirm this is electric</comment>
        </item>
        <item partNum="926-AA">
            <productName>Baby Monitor</productName>
            <quantity>1</quantity>
            <USPrice>39.98</USPrice>
            <shipDate>1999-05-21</shipDate>
        </item>
    </items>
</purchaseOrder>
'
```

Rejection case; document is invalid per schema:

```
curl -i -H content-type:application/xml \
  https://$ORG-$ENV.apigee.net/java-xsd/t1 \
  -d '<purchaseOrder xmlns="http://tempuri.org/po.xsd" orderDate="2018-07-09">
         <name>Alice Smith</name>
        <street>123 Maple Street</street>
        <city>Mill Valley</city>
        <state>CA</state>
        <zip>90952</zip>
     <billTo country="US">
        <name>Robert Smith</name>
        <street>8 Oak Avenue</street>
        <city>Old Town</city>
        <state>PA</state>
        <zip>95819</zip>
    </billTo>
 </purchaseOrder>
'
```

The response in this case is:
```
HTTP/1.1 200 OK
Date: Tue, 13 Nov 2018 19:25:42 GMT
Content-Type: application/json
Content-Length: 249
Connection: keep-alive

{
  "valid" : "false",
  "message" : "1. org.xml.sax.SAXParseException; lineNumber: 2; columnNumber: 16; cvc-complex-type.2.4.a: Invalid content was found starting with element 'name'. One of '{\"http://tempuri.org/po.xsd\":shipTo}' is expected."
}

```

## Building

Building from source requires Java 1.8, and Maven.

1. unpack (if you can read this, you've already done that).

2. Before building _the first time_, configure the build on your machine by loading the Apigee jars into your local cache:
   ```
   ./buildsetup.sh
   ```

3. Build with maven.
   ```
   mvn clean package
   ```
   This will build the jar and also run all the tests.


Pull requests are welcomed!


## Runtime Dependencies

This callout depends on Google Guava v25.1-jre or later.  While
that JAR is not documented as being part of the Apigee SaaS, in practice the
Guava JAR is available at runtime, so there is no need to include it in your API
proxy.  All you need to include is the base JAR from this repo.


## License

This material is Copyright 2017-2020, Google LLC.
and is licensed under the [Apache 2.0 License](LICENSE). This includes the Java code as well as the API Proxy configuration.


## Support

This callout is open-source software, and is not a supported part of Apigee Edge.
If you need assistance, you can try inquiring on
[The Apigee Community Site](https://community.apigee.com).  There is no service-level
guarantee for responses to inquiries regarding this callout.


## Bugs

* Instances of the SchemaFactory are not pooled. Doing so might improve perf at
  load.

* The tests retrieve XSD from the w3c site, which causes them to be slow.
