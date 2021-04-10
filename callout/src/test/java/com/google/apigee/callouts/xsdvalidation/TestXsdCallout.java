// Copyright 2017-2020 Google LLC.
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

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import mockit.Mock;
import mockit.MockUp;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TestXsdCallout {
  private static final String testDataDir = "src/test/resources/test-data";

  MessageContext messageContext;
  InputStream messageContentStream;
  Message message;
  ExecutionContext exeCtxt;

  @BeforeMethod()
  public void beforeMethod() {

    messageContext =
        new MockUp<MessageContext>() {
          private Map variables;

          public void $init() {
            variables = new HashMap();
          }

          @Mock()
          public <T> T getVariable(final String name) {
            if (variables == null) {
              variables = new HashMap();
            }
            return (T) variables.get(name);
          }

          @Mock()
          public boolean setVariable(final String name, final Object value) {
            if (variables == null) {
              variables = new HashMap();
            }
            variables.put(name, value);
            return true;
          }

          @Mock()
          public boolean removeVariable(final String name) {
            if (variables == null) {
              variables = new HashMap();
            }
            if (variables.containsKey(name)) {
              variables.remove(name);
            }
            return true;
          }

          @Mock()
          public Message getMessage() {
            return message;
          }
        }.getMockInstance();

    exeCtxt = new MockUp<ExecutionContext>() {}.getMockInstance();

    message =
        new MockUp<Message>() {
          @Mock()
          public InputStream getContentAsStream() {
            // new ByteArrayInputStream(messageContent.getBytes(StandardCharsets.UTF_8));
            return messageContentStream;
          }
        }.getMockInstance();
  }

  @DataProvider(name = "batch1")
  public static Object[][] getDataForBatch1() throws IOException, IllegalStateException {

    // @DataProvider requires the output to be a Object[][]. The inner
    // Object[] is the set of params that get passed to the test method.
    // So, if you want to pass just one param to the constructor, then
    // each inner Object[] must have length 1.

    ObjectMapper om = new ObjectMapper();
    om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    // Path currentRelativePath = Paths.get("");
    // String s = currentRelativePath.toAbsolutePath().toString();
    // System.out.println("Current relative path is: " + s);

    // read in all the *.json files in the test-data directory
    File testDir = new File(testDataDir);
    if (!testDir.exists()) {
      throw new IllegalStateException("no test directory.");
    }
    File[] files = testDir.listFiles();
    if (files.length == 0) {
      throw new IllegalStateException("no tests found.");
    }
    Arrays.sort(files);
    int c = 0;
    ArrayList<TestCase> list = new ArrayList<TestCase>();
    for (File file : files) {
      String name = file.getName();
      if (name.matches("^[0-9]+.+.json$")) {
        TestCase tc = om.readValue(file, TestCase.class);
        tc.setTestName(name.substring(0, name.length() - 5));
        list.add(tc);
      }
    }

    return list.stream().map(tc -> new TestCase[] {tc}).toArray(Object[][]::new);
  }

  @Test
  public void testDataProviders() throws IOException {
    Assert.assertTrue(getDataForBatch1().length > 0);
  }

  private static String resolveFileReference(String ref) throws IOException {
    return new String(Files.readAllBytes(Paths.get(testDataDir, ref.substring(7, ref.length()))));
  }

  private InputStream getInputStream(TestCase tc) throws Exception {
    if (tc.getInput() != null) {
      Path path = Paths.get(testDataDir, tc.getInput());
      if (!Files.exists(path)) {
        throw new IOException("file(" + tc.getInput() + ") not found");
      }
      return Files.newInputStream(path);
    }

    // readable empty stream
    return new ByteArrayInputStream(new byte[] {});
  }

  @Test(dataProvider = "batch1")
  public void test2_Configs(TestCase tc) throws Exception {
    if (tc.getDescription() != null)
      System.out.printf("\n  %-40s - %s\n", tc.getTestName(), tc.getDescription());
    else System.out.printf("\n  %-40s\n", tc.getTestName());

    // set variables into message context
    for (Map.Entry<String, String> entry : tc.getContext().entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (value.startsWith("file://")) {
        value = resolveFileReference(value);
      }
      messageContext.setVariable(key, value);
    }

    messageContentStream = getInputStream(tc);

    XsdValidatorCallout callout = new XsdValidatorCallout(tc.getProperties());
    ExecutionResult actualResult = callout.execute(messageContext, exeCtxt);
    String s = tc.getExpected().get("success").toString();
    ExecutionResult expectedResult =
        (s != null && s.toLowerCase().equals("true"))
            ? ExecutionResult.SUCCESS
            : ExecutionResult.ABORT;

    String messages = messageContext.getVariable("xsd_validation_exceptions");
    if (messages != null) {
      System.out.printf("\n  ** Generated Exceptions during Validation:\n%s\n", messages);
    }
    if (expectedResult == actualResult) {
      if (expectedResult == ExecutionResult.SUCCESS) {
        Object o = tc.getExpected().get("valid");
        Assert.assertNotNull(o, tc.getTestName() + ": broken; no expected validity specified");
        String b = o.toString();
        Assert.assertNotNull(b, tc.getTestName() + ": broken; no expected validity specified");
        boolean expectedValidity = Boolean.parseBoolean(b);
        boolean actualValidity = messageContext.getVariable("xsd_valid");
        Assert.assertEquals(
            actualValidity, expectedValidity, tc.getTestName() + ": validity not as expected");
        o = tc.getExpected().get("error");
        if (o != null) {
          String expectedError = o.toString();
          String observedError = messageContext.getVariable("xsd_error");
          Assert.assertNotNull(
              observedError,
              tc.getTestName() + ": no error observed, expected (" + expectedError + ")");
          Assert.assertTrue(
              observedError.endsWith(expectedError),
              String.format(
                  "%s: error mismatch, expected(%s) actual(%s)",
                  tc.getTestName(), expectedError, observedError));
        }
      } else {
        String expectedError = tc.getExpected().get("error").toString();
        Assert.assertNotNull(
            expectedError, tc.getTestName() + ": broken; no expected error specified");
        String actualError = messageContext.getVariable("xsd_error");
        Assert.assertTrue(
            actualError.endsWith(expectedError),
            String.format(
                "%s: error mismatch, expected(%s) actual(%s)",
                tc.getTestName(), expectedError, actualError));
      }
      if (messages != null) {
        int lines = countLines(messages);
        Object o = tc.getExpected().get("exceptionCount");
        if (o != null) {
          int expectedExceptionCount = (int) o;
          Assert.assertEquals(
              lines, expectedExceptionCount, tc.getTestName() + ": exception count");
        }
      }

      Map<String, Object> outputVariableMap =
          (Map<String, Object>) tc.getExpected().get("context-variables");
      if (outputVariableMap != null) {
        for (String key : outputVariableMap.keySet()) {
          System.out.printf("** Examining ctxt var (%s)\n", key);

          Object objValue = outputVariableMap.get(key);
          if (objValue instanceof Double) {
            Assert.assertEquals(
                new Double(messageContext.getVariable(key).toString()),
                (Double) objValue,
                tc.getTestName() + ": context-variable:" + key);
          } else if (objValue instanceof Long) {
            Assert.assertEquals(
                new Long(messageContext.getVariable(key)),
                (Long) objValue,
                tc.getTestName() + ": context-variable:" + key);
          } else {
            String expectedStringValue = objValue.toString();
            Object actualObjectValue = messageContext.getVariable(key);
            Assert.assertNotNull(actualObjectValue, tc.getTestName() + ": context-variable:" + key);
            String actualStringValue = actualObjectValue.toString();
            Assert.assertEquals(
                actualStringValue,
                expectedStringValue,
                tc.getTestName() + ": context-variable:" + key);
          }
        }
      }
    } else {
      String observedError = messageContext.getVariable("xsd_error");
      System.err.printf("    unexpected error: %s\n", observedError);
      Assert.assertEquals(
          actualResult, expectedResult, tc.getTestName() + ": result not as expected");
    }
    System.out.println("=========================================================");
  }

  public static int countLines(String str) {
    if (str == null || str.isEmpty()) return 0;
    int lines = 1;
    int pos = 0;
    while ((pos = str.indexOf("\n", pos) + 1) != 0) {
      lines++;
    }
    return lines;
  }
}
