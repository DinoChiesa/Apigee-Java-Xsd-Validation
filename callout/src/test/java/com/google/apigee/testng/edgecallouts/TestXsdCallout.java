package com.google.apigee.testng.edgecallouts;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import mockit.Mock;
import mockit.MockUp;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.message.MessageContext;
import com.apigee.flow.message.Message;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.google.apigee.edgecallouts.xsdvalidation.XsdValidatorCallout;

public class TestXsdCallout {
    private final static String testDataDir = "src/test/resources/test-data";

    MessageContext msgCtxt;
    InputStream messageContentStream;
    Message message;
    ExecutionContext exeCtxt;

    @BeforeMethod()
    public void beforeMethod() {

        msgCtxt = new MockUp<MessageContext>() {
            private Map variables;
            public void $init() {
                variables = new HashMap();
            }

            @Mock()
            public <T> T getVariable(final String name){
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

        exeCtxt = new MockUp<ExecutionContext>(){ }.getMockInstance();

        message = new MockUp<Message>(){
            @Mock()
            public InputStream getContentAsStream() {
                // new ByteArrayInputStream(messageContent.getBytes(StandardCharsets.UTF_8));
                return messageContentStream;
            }
        }.getMockInstance();
    }


    @DataProvider(name = "batch1")
    public static Object[][] getDataForBatch1()
        throws IOException, IllegalStateException {

        // @DataProvider requires the output to be a Object[][]. The inner
        // Object[] is the set of params that get passed to the test method.
        // So, if you want to pass just one param to the constructor, then
        // each inner Object[] must have length 1.

        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, true);

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
        int c=0;
        ArrayList<TestCase> list = new ArrayList<TestCase>();
        for (File file : files) {
            String name = file.getName();
            if (name.matches("^[0-9]+.+.json$")) {
                TestCase tc = om.readValue(file, TestCase.class);
                tc.setTestName(name.substring(0,name.length()-5));
                list.add(tc);
            }
        }

        // OMG!!  Seriously? Is this the easiest way to generate a 2-d array?
        int n = list.size();
        Object[][] data = new Object[n][];
        for (int i = 0; i < data.length; i++) {
            data[i] = new Object[]{ list.get(i) };
        }
        return data;
    }

    @Test
    public void testDataProviders() throws IOException {
        Assert.assertTrue(getDataForBatch1().length > 0);
    }

    private static String resolveFileReference(String ref) throws IOException {
        return new String(Files.readAllBytes(Paths.get(testDataDir, ref.substring(7,ref.length()))));
    }

    private InputStream getInputStream(TestCase tc) throws Exception {
        if (tc.getInput()!=null) {
            Path path = Paths.get(testDataDir, tc.getInput());
            if (!Files.exists(path)) {
                throw new IOException("file("+tc.getInput()+") not found");
            }
            return Files.newInputStream(path);
        }

        // readable empty stream
        return new ByteArrayInputStream(new byte[] {});
    }

    @Test(dataProvider = "batch1")
    public void test2_Configs(TestCase tc) throws Exception {
        if (tc.getDescription()!= null)
            System.out.printf("  %10s - %s\n", tc.getTestName(), tc.getDescription() );
        else
            System.out.printf("  %10s\n", tc.getTestName() );

        // set variables into message context
        for (Map.Entry<String, String> entry : tc.getContext().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value.startsWith("file://")) {
                value = resolveFileReference(value);
            }
            msgCtxt.setVariable(key, value);
        }

        messageContentStream = getInputStream(tc);

        XsdValidatorCallout callout = new XsdValidatorCallout(tc.getProperties());
        ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
        String s = tc.getExpected().get("success").toString();
        ExecutionResult expectedResult = (s!=null && s.toLowerCase().equals("true")) ?
                                           ExecutionResult.SUCCESS : ExecutionResult.ABORT;

        if (expectedResult == actualResult) {
            if (expectedResult == ExecutionResult.SUCCESS) {
                Object o = tc.getExpected().get("valid");
                Assert.assertNotNull(o, tc.getTestName() + ": broken; no expected validity specified");
                String b = o.toString();
                Assert.assertNotNull(b, tc.getTestName() + ": broken; no expected validity specified");
                boolean expectedValidity = Boolean.parseBoolean(b);
                boolean actualValidity = msgCtxt.getVariable("xsd_valid");
                Assert.assertEquals(actualValidity, expectedValidity, tc.getTestName() + ": validity not as expected");
                o = tc.getExpected().get("error");
                if (o != null) {
                    String expectedError = o.toString();
                    String observedError = msgCtxt.getVariable("xsd_error");
                    Assert.assertNotNull(observedError, tc.getTestName() + ": no error observed, expected ("+ expectedError +")");
                    Assert.assertTrue(observedError.endsWith(expectedError), tc.getTestName() + ": error not as expected (" + observedError + ")");
                }
            }
            else {
                String expectedError = tc.getExpected().get("error").toString();
                Assert.assertNotNull(expectedError, tc.getTestName() + ": broken; no expected error specified");
                String actualError = msgCtxt.getVariable("xsd_error");
                Assert.assertTrue(actualError.endsWith(expectedError), tc.getTestName() + ": error not as expected (" + actualError + ")");
            }
        }
        else {
            String observedError = msgCtxt.getVariable("xsd_error");
            System.err.printf("    unexpected error: %s\n", observedError);
            Assert.assertEquals(actualResult, expectedResult, tc.getTestName() + ": result not as expected");
        }
        System.out.println("=========================================================");
    }

}
