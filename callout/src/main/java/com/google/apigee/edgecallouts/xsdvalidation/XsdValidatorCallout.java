// XsdValidatorCallout.java
//
// A callout for Apigee Edge that performs a validation of an XML document against an XSD.
//
// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Example configuration
//
// <JavaCallout name='Java-Xsd-Validation'>
//   <Properties>
//     <!-- specify the XSD itself in one of these ways -->
//     <Property name='xsd'>file://xslt-filename.xsd</Property> <!-- resource in jar -->
//     <Property name='xsd'>http://hostname/url-returning-an-xsd</Property>
//     <Property name='xsd'>immediate-string-containing-xsd</Property>
//     <Property name='xsd'>{variable-containing-one-of-the-above}</Property>
//
//     <!-- The document to be validated.  If of type Message, then policy will use x.content -->
//     <Property name='source'>name-of-variable-containing-XML-doc</Property>
//
//     <!-- where to put the transformed data. If none, put in message.content -->
//     <Property name='output'>name-of-variable-to-hold-output</Property>
//
//     <!-- arbitrary params to pass to the XSLT -->
//     <Property name='param_x'>string value of param</Property>
//     <Property name='param_y'>{variable-containing-value-of-param}</Property>
//     <Property name='param_z'>file://something.xsd</Property> <!-- resource in jar -->
//   </Properties>
//   <ClassName>com.dinochiesa.xslt.XsltCallout</ClassName>
//   <ResourceURL>java://edgecallout-xslt.jar</ResourceURL>
// </JavaCallout>
//
//
// ----------------------------------------------------------
//
// This software is licensed under the Apache Source license 2.0.
// See the accompanying LICENSE file.
//
// Copyright (c) 2015, 2016 by Dino Chiesa and Apigee Corporation, 2017 Google Inc.
//

package com.google.apigee.edgecallouts.xsdvalidation;


import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.IOIntensive;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.exception.ExceptionUtils;


@IOIntensive
public class XsdValidatorCallout implements Execution {
    private static final String varPrefix = "xsd_";
    private static final String variableReferencePatternString = "(.*?)\\{([^\\{\\}]+?)\\}(.*?)";
    private final static Pattern variableReferencePattern = Pattern.compile(variableReferencePatternString);
    private static final String urlReferencePatternString = "^(https?://)(.+)$";
    private final static Pattern urlReferencePattern = Pattern.compile(urlReferencePatternString);
    private LoadingCache<String, String> fileResourceCache;
    private LoadingCache<String, String> urlResourceCache;
    private Map<String,String> properties; // read-only

    public XsdValidatorCallout (Map properties) {
        // convert the untyped Map to a generic map
        Map<String,String> m = new HashMap<String,String>();
        Iterator iterator = properties.keySet().iterator();
        while(iterator.hasNext()){
            Object key = iterator.next();
            Object value = properties.get(key);
            if ((key instanceof String) && (value instanceof String)) {
                m.put((String) key, (String) value);
            }
        }
        this.properties = Collections.unmodifiableMap(m);

        fileResourceCache = CacheBuilder.newBuilder()
            .concurrencyLevel(4)
            .maximumSize(1048000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, String>() {
                    public String load(String key) throws IOException {
                        InputStream in = null;
                        String s = "";
                        try {
                            in = getResourceAsStream(key);
                            byte[] fileBytes = new byte[in.available()];
                            in.read(fileBytes);
                            in.close();
                            s = new String(fileBytes, StandardCharsets.UTF_8);
                        }
                        catch (java.lang.Exception exc1) {
                            // gulp
                        }
                        finally {
                            if (in!=null) { in.close(); }
                        }
                        return s.trim();
                    }
                });

        urlResourceCache = CacheBuilder.newBuilder()
            .concurrencyLevel(4)
            .maximumSize(1048000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, String>() {
                    public String load(String key) throws IOException {
                        InputStream in = null;
                        String s = "";
                        try {
                            URL url = new URL(key);
                            in = url.openStream ();
                            s = new String(IOUtils.toByteArray(in), StandardCharsets.UTF_8);
                        }
                        catch (java.lang.Exception exc1) {
                            // gulp
                        }
                        finally {
                            if (in!=null) { in.close(); }
                        }
                        return s.trim();
                    }
                });
    }

    private static String varName(String s) {
        return varPrefix + s;
    }

    private String getSourceProperty() {
        String sourceProp = (String) this.properties.get("source");
        return (sourceProp == null || sourceProp.equals("")) ? "message" : sourceProp;
    }

    private boolean getDebug() {
        String value = (String) this.properties.get("debug");
        if (value == null) return false;
        if (value.trim().toLowerCase().equals("true")) return true;
        return false;
    }

    private Source getSource(MessageContext msgCtxt) throws IOException {
        String sourceProp = getSourceProperty();
        Source source = null;
        Object in = msgCtxt.getVariable(sourceProp);
        if (in == null) {
            throw new IllegalStateException(String.format("source '%s' is empty", sourceProp));
        }
        if (in instanceof com.apigee.flow.message.Message) {
            Message msg = (Message) in;
            source = new StreamSource(msg.getContentAsStream());
        }
        else {
            // assume it resolves to an xml string
            String s = (String) in;
            s = s.trim();
            if (!s.startsWith("<")) {
                throw new IllegalStateException(String.format("source '%s' does not appear to be XML", sourceProp));
            }
            InputStream s2 = IOUtils.toInputStream(s, "UTF-8");
            source = new StreamSource(s2);
        }
        return source;
    }

    private StreamSource[] getXsd(MessageContext msgCtxt)
        throws IllegalStateException, IOException, ExecutionException {
        String xsdValue = (String) this.properties.get("xsd");
        Source source = null;
        if (xsdValue == null) {
            throw new IllegalStateException("configuration error: no xsd property");
        }
        if (xsdValue.equals("")) {
            throw new IllegalStateException("configuration error: xsd property is empty");
        }
        xsdValue = resolvePropertyValue(xsdValue, msgCtxt);
        if (xsdValue == null || xsdValue.equals("")) {
            throw new IllegalStateException("configuration error: xsd resolves to null or empty");
        }

        xsdValue = xsdValue.trim();
        StreamSource[] sources;
        if (xsdValue.startsWith("<")) {
            sources = new StreamSource[1];
            sources[0] = new StreamSource(new StringReader(maybeResolveUrlReference(xsdValue)));
        }
        else {
            String[] parts = xsdValue.split(",");
            //System.out.printf("\n** found %d xsds\n", parts.length);
            sources = new StreamSource[parts.length];
            for (int i=0; i < parts.length; i++) {
                //System.out.printf("** part[%d] = '%s'\n", i, parts[i]);
                sources[i] = new StreamSource(new StringReader(maybeResolveUrlReference(parts[i])));
            }
        }
        return sources;
    }

    // If the value of a property contains a pair of curlies,
    // eg, {apiproxy.name}, then "resolve" the value by de-referencing
    // the context variable whose name appears between the curlies.
    // If the variable name is not known, then it returns a null.
    protected String resolvePropertyValue(String spec, MessageContext msgCtxt) {
        Matcher matcher = variableReferencePattern.matcher(spec);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "");
            sb.append(matcher.group(1));
            Object v = msgCtxt.getVariable(matcher.group(2));
            if (v != null){
                sb.append((String) v );
            }
            sb.append(matcher.group(3));
        }
        matcher.appendTail(sb);
        return (sb.length() > 0) ? sb.toString() : null;
    }

    private static InputStream getResourceAsStream(String resourceName) throws IOException {
        if (!resourceName.startsWith("/")) {
            resourceName = "/" + resourceName;
        }
        InputStream in = XsdValidatorCallout.class.getResourceAsStream(resourceName);
        if (in == null) {
            throw new IOException("resource \"" + resourceName + "\" not found");
        }
        return in;
    }

    private String maybeResolveUrlReference(String ref) throws ExecutionException {
        if (ref.startsWith("file://")) {
            return fileResourceCache.get(ref.substring(7,ref.length()));
        }
        Matcher m = urlReferencePattern.matcher(ref);
        if (m.find()) {
            return urlResourceCache.get(ref);
        }
        return ref;
    }

    public ExecutionResult execute(MessageContext msgCtxt, ExecutionContext exeCtxt) {
        ExecutionResult calloutResult = ExecutionResult.ABORT;
        CustomValidationErrorHandler errorHandler = null;
        boolean debug = getDebug();
        try {
            SchemaFactory notThreadSafeFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            StreamSource[] sources = getXsd(msgCtxt);
            // the schema order is important.
            // If A imports B, then you must have B before A in the array you use to create the schema.
            Schema schema = notThreadSafeFactory.newSchema(sources);
            Validator validator = schema.newValidator();
            errorHandler = new CustomValidationErrorHandler(msgCtxt, debug);
            validator.setErrorHandler(errorHandler);
            Source source = getSource(msgCtxt);
            validator.validate(source);
            msgCtxt.setVariable(varName("valid"), errorHandler.isValid());
            calloutResult = ExecutionResult.SUCCESS;
        }
        catch (Exception e) {
            if (debug) {
                String stacktrace = ExceptionUtils.getStackTrace(e);
                //System.out.println(stacktrace);  // to MP stdout
                msgCtxt.setVariable(varName("stacktrace"), stacktrace);
            }
            String error = e.toString();
            msgCtxt.setVariable(varName("exception"), error);
            msgCtxt.setVariable(varName("error"), error);
        }
        finally {
            if (errorHandler != null) {
                String consolidatedExceptionMessage = errorHandler.getConsolidatedExceptionMessage();
                if (consolidatedExceptionMessage != null) {
                    msgCtxt.setVariable(varName("validation_exceptions"), consolidatedExceptionMessage);
                }
            }
        }
        return calloutResult;
    }
}
