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
package com.google.apigee.edgecallouts.xsdvalidation;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import com.apigee.flow.message.MessageContext;

public class CustomValidationErrorHandler implements ErrorHandler {

    private final static String _prefix = "xsd_";
    MessageContext _msgCtxt;
    int _warnCount;
    int _errorCount;
    boolean _debug = false;
    private static String varName(String s) {
        return _prefix + s;
    }

    public CustomValidationErrorHandler(MessageContext msgCtxt) {
        _msgCtxt = msgCtxt;
        _warnCount = 0;
        _errorCount = 0;
    }
    public CustomValidationErrorHandler(MessageContext msgCtxt, boolean debug) {
        _msgCtxt = msgCtxt;
        _warnCount = 0;
        _errorCount = 0;
        _debug = debug;
    }
    public void error(SAXParseException exception) {
        _errorCount++;
        if (_debug) {
            System.out.printf("Error\n");
            exception.printStackTrace();
        }
        _msgCtxt.setVariable(varName("error_" + _errorCount), "Error:" + exception.toString());
    }
    public void fatalError(SAXParseException exception) {
        _errorCount++;
        if (_debug) {
            System.out.printf("Fatal\n");
            exception.printStackTrace();
        }
        _msgCtxt.setVariable(varName("error_" + _errorCount), "Fatal Error:" + exception.toString());
    }

    public void warning(SAXParseException exception) {
        _warnCount++;
        if (_debug) {
            System.out.printf("Warning\n");
            exception.printStackTrace();
        }
        _msgCtxt.setVariable(varName("warning_" + _warnCount), "Warning:" + exception.toString());
    }

    public boolean isValid() {
        return this._errorCount == 0;
    }

    public int getErrorCount() {
        return this._errorCount;
    }
}
