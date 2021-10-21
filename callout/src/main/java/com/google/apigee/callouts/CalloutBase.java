// Copyright 2018-2021 Google LLC
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

package com.google.apigee.callouts;

import com.apigee.flow.message.MessageContext;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CalloutBase {
  protected static final String variableReferencePatternString = "(.*?)\\{([^\\{\\} ]+?)\\}(.*?)";
  protected static final Pattern variableReferencePattern =
      Pattern.compile(variableReferencePatternString);
  private static final String commonError = "^(.+?)[:;] (.+)$";
  private static final Pattern commonErrorPattern = Pattern.compile(commonError);

  protected Map<String, Object> properties; // read-only
  protected List<String> multivaluedProperties = Arrays.asList(new String[] {""});

  public CalloutBase(Map properties) {
    // convert the untyped Map to a generic map
    Map<String, Object> m = new HashMap<String, Object>();
    Iterator iterator = properties.keySet().iterator();
    while (iterator.hasNext()) {
      String key = (String) iterator.next();
      Object value = properties.get(key);
      if (multivaluedProperties.contains(key)) {
        if (m.containsKey(key)) {
          ((List) (m.get(key))).add(value);
        } else if (value instanceof List) {
          m.put(key, value);
        } else {
          List<String> newList = new ArrayList<String>();
          newList.add(value.toString());
          m.put(key, newList);
        }
      } else {
        m.put((String) key, value.toString());
      }
    }
    this.properties = Collections.unmodifiableMap(m);
  }

  public abstract String getVarnamePrefix();

  protected String varName(String s) {
    return getVarnamePrefix() + s;
  }

  protected String getOutputVar(MessageContext msgCtxt) throws Exception {
    String dest = getSimpleOptionalProperty("output-variable", msgCtxt);
    if (dest == null) {
      dest = getSimpleOptionalProperty("output", msgCtxt);
      if (dest == null) {
        return "message.content";
      }
    }
    return dest;
  }

  protected boolean getDebug() {
    String wantDebug = (String) this.properties.get("debug");
    boolean debug = (wantDebug != null) && Boolean.parseBoolean(wantDebug);
    return debug;
  }

  protected String normalizeString(String s) {
    s = s.replaceAll("^ +", "");
    s = s.replaceAll("(\r|\n) +", "\n");
    return s.trim();
  }

  protected String getSimpleRequiredProperty(String propName, MessageContext msgCtxt)
      throws Exception {
    String value = (String) this.properties.get(propName);
    if (value == null) {
      throw new IllegalStateException(
          String.format("configuration error: %s resolves to an empty string", propName));
    }
    value = value.trim();
    if (value.equals("")) {
      throw new IllegalStateException(
          String.format("configuration error: %s resolves to an empty string", propName));
    }
    value = resolvePropertyValue(value, msgCtxt);
    if (value == null || value.equals("")) {
      throw new IllegalStateException(
          String.format("configuration error: %s resolves to an empty string", propName));
    }
    return value;
  }

  protected String getSimpleOptionalProperty(String propName, MessageContext msgCtxt)
      throws Exception {
    Object value = this.properties.get(propName);
    if (value == null) {
      return null;
    }
    String v = (String) value;
    v = v.trim();
    if (v.equals("")) {
      return null;
    }
    v = resolvePropertyValue(v, msgCtxt);
    if (v == null || v.equals("")) {
      return null;
    }
    return v;
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
      if (v != null) {
        sb.append((String) v);
      }
      sb.append(matcher.group(3));
    }
    matcher.appendTail(sb);
    return (sb.length() > 0) ? sb.toString() : null;
  }

  protected static String getStackTraceAsString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    return sw.toString();
  }

  protected void setExceptionVariables(Exception exc1, MessageContext msgCtxt) {
    String error = exc1.toString().replaceAll("\n", " ");
    msgCtxt.setVariable(varName("exception"), error);
    Matcher matcher = commonErrorPattern.matcher(error);
    if (matcher.matches()) {
      msgCtxt.setVariable(varName("error"), matcher.group(2));
    } else {
      msgCtxt.setVariable(varName("error"), error);
    }
  }
}
