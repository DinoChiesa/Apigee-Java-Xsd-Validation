// Copyright 2017-2020 Google LLC
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
package com.google.apigee.callouts.xsdvalidation;

import com.apigee.flow.message.MessageContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.validation.Validator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

public class CustomValidationErrorHandler implements ErrorHandler {
  private static final int RECORDED_EXCEPTION_LIMIT = 10;
  private static final String _prefix = "xsd_";
  MessageContext _msgCtxt;
  int _warnCount;
  int _errorCount;
  boolean _debug = false;
  List<String> exceptionList;
  List<String> pathList;
  Validator validator;

  private static String varName(String s) {
    return _prefix + s;
  }

  // public CustomValidationErrorHandler(MessageContext msgCtxt) {
  //     this(msgCtxt, false);
  // }
  public CustomValidationErrorHandler(MessageContext msgCtxt, Validator validator, boolean debug) {
    _msgCtxt = msgCtxt;
    _warnCount = 0;
    _errorCount = 0;
    this.validator = validator;
    _debug = debug;
  }

  public void error(SAXParseException exception) {
    _errorCount++;
    if (_debug) {
      System.out.printf("Error\n");
      exception.printStackTrace();
    }
    _msgCtxt.setVariable(varName("error_" + _errorCount), "Error:" + exception.toString());
    addException(exception);
  }

  public void fatalError(SAXParseException exception) {
    _errorCount++;
    if (_debug) {
      System.out.printf("Fatal\n");
      exception.printStackTrace();
    }
    _msgCtxt.setVariable(varName("error_" + _errorCount), "Fatal Error:" + exception.toString());
    addException(exception);
  }

  public void warning(SAXParseException exception) {
    _warnCount++;
    if (_debug) {
      System.out.printf("Warning\n");
      exception.printStackTrace();
    }
    _msgCtxt.setVariable(varName("warning_" + _warnCount), "Warning:" + exception.toString());
    addException(exception);
  }

  private static String getFullPathOfElement(Node element) {
    String path = null;
    Node node = element;
    if (node != null) {
      while (node != null) {
        path = (path == null) ? node.getNodeName() : node.getNodeName() + '/' + path;
        node = node.getParentNode();
      }
    }
    return path;
  }

  private void addException(SAXParseException ex) {
    if (this.exceptionList == null) this.exceptionList = new ArrayList<>(); // lazy create
    if (exceptionList.size() < RECORDED_EXCEPTION_LIMIT) this.exceptionList.add(ex.toString());
    if (this.pathList == null) this.pathList = new ArrayList<>(); // lazy create
    if (pathList.size() < RECORDED_EXCEPTION_LIMIT) {
      try {
        Element curElement =
            (Element)
                validator.getProperty("http://apache.org/xml/properties/dom/current-element-node");

        if (curElement != null) {
          this.pathList.add(getFullPathOfElement(curElement));
        }
      } catch (Exception purposefullyIgnoredNestedException) {
        // purposefullyIgnoredNestedException.printStackTrace(System.out);
      }
    }
  }

  public boolean isValid() {
    return this._errorCount == 0;
  }

  public int getErrorCount() {
    return this._errorCount;
  }

  public String getPaths() {
    if (this.pathList == null) return null;
    LineCounter lc = new LineCounter();
    return (String) pathList.stream().collect(Collectors.joining(","));
  }

  public String getConsolidatedExceptionMessage() {
    if (this.exceptionList == null) return null;
    LineCounter lc = new LineCounter();
    return (String) exceptionList.stream().map(lc::toIndexed).collect(Collectors.joining("\n"));
  }

  static class LineCounter {
    int n = 1;

    public String toIndexed(String line) {
      return (n++) + ". " + line;
    }
  }
}
