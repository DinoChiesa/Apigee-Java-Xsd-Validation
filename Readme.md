# Java callout for XSD Validation

This directory contains the Java source code required to compile a Java
callout for Apigee Edge that does Validation of an XML document against an XSD. There's a built-in policy that
does this; this callout is different in that it is a bit more flexible.

* the person configuring the policy can specify the XSD sheet in a context variable.
  This is nice because it means the XSD can be dynamically determined at runtime.
* It is possible to specify an XSD source available at an HTTP endpoint


## Using this policy

You do not need to build the source code in order to use the policy in Apigee Edge.
All you need is the built JAR, and the appropriate configuration for the policy.
If you want to build it, feel free.  The instructions are at the bottom of this readme.


1. copy the jar file, available in  target/edge-custom-xsd-validation-1.0.1.jar , if you have built the jar, or in [the repo](bundle/apiproxy/resources/java/edge-custom-xsd-validation-1.0.1.jar) if you have not, to your apiproxy/resources/java directory. Also copy all the required dependencies. (See below) You can do this offline, or using the graphical Proxy Editor in the Apigee Edge Admin Portal.

2. include a Java callout policy in your
   apiproxy/resources/policies directory. It should look
   like this:
   ```xml
    <JavaCallout name='Java-Xsd'>
      <Properties>
        <Property name='xsd'>file://xsd-name-here.xsd</Property>
           ....
      </Properties>
      <ClassName>com.google.apigee.edgecallouts.xsdvalidation.XsdValidatorCallout</ClassName>
      <ResourceURL>java://edge-custom-xsd-validation-1.0.1.jar</ResourceURL>
    </JavaCallout>
   ```

5. use the Edge UI, or a command-line tool like pushapi (See
   https://github.com/carloseberhardt/apiploy) or similar to
   import the proxy into an Edge organization, and then deploy the proxy .

6. use a client to generate and send http requests to tickle the proxy.



## Notes

There is one callout class, com.google.apigee.edgecallouts.xsdvalidation.XsdValidatorCallout,
which performs the XSD validation.

You must configure the callout with Property elements in the policy configuration.

Examples follow.

To use this callout, you will need an API Proxy, of course.

## Example 1: Perform a simple validation

```xml
<JavaCallout name='JavaCallout-Xslt-1'>
  <Properties>
     <Property name='xsd'>{xsdurl}</Property>
     <Property name='source'>request</Property>
  </Properties>
  <ClassName>com.google.apigee.edgecallouts.xsdvalidation.XsdValidatorCallout</ClassName>
  <ResourceURL>java://edge-custom-xsd-validation-1.0.1.jar</ResourceURL>
</JavaCallout>
```

The xsd property specifies the schema. This can be one of 4 forms:

* a file reference, like file://filename.xsd
* a url beginning with http:// or https://
* a string that begins with <xsd:schema and ends with /xsd:schema>. In other words, you can directly embed the XSD into the configuration for the policy.
* a variable enclosed in curly-braces that resolves to one of the above.

If a filename, the file must be present as a resource in the JAR file.
This requires you to re-package the jar file. The structure of the jar
must be like so:

```
meta-inf/
meta-inf/manifest.mf
com/
com/google/
com/google/apigee/
com/google/apigee/edgecallouts/
com/google/apigee/edgecallouts/xsdvalidation
com/google/apigee/edgecallouts/xsdvalidation/XsdValidatorCallout.class
resources/
resources/filename.xsd
```

You can have as many XSDs in the resources directory as you like.

If a URL, the URL must return a valid XSD. The URL should be accessible
from the message processor. The contents of the URL will be cached, currently for 10 minutes. This cache period is not confgurable, but you could change it in the source and re-compile if you like. 

The source property specifies where to find the XML to be validated. This must be a variable name.  Do not use curly-braces. If
this variable resolves to a Message, then the callout will validate
Message.content.

The result of the policy is to set a variable "xsd_valid".
It will hold "true" if valid; "false" if not.



## Building

Building from source requires Java 1.7, and Maven.

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


## Build Dependencies

- Apigee Edge expressions v1.0
- Apigee Edge message-flow v1.0
- Apache commons lang 2.6
- Apache commons validator 1.4.1
- Apache commons io 2.0.1


## License

This material is Copyright 2017, Google Inc.
and is licensed under the [Apache 2.0 License](LICENSE). This includes the Java code as well as the API Proxy configuration.



## Bugs

* The tests are incomplete.
* There is no sample API Proxy bundle.
