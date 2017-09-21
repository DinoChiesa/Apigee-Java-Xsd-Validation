package com.google.apigee.testng.edgecallouts;

import java.util.HashMap;

public class TestCase {

    private String _testName;
    private String _description;
    private String _input; // filename
    private HashMap<String,Object> _properties; // JSON hash
    private HashMap<String,Object> _expected; // JSON hash
    private HashMap<String,String> _context; // JSON hash

    // getters
    public String getTestName() { return _testName; }
    public String getDescription() { return _description; }
    public String getInput() { return _input; }
    public HashMap<String,Object> getProperties() { return _properties; }
    public HashMap<String,String> getContext() { return _context; }
    public HashMap<String,Object> getExpected() { return _expected; }

    // setters
    public void setTestName(String n) { _testName = n; }
    public void setDescription(String d) { _description = d; }
    public void setInput(String f) { _input = f; }
    public void setExpected(HashMap<String,Object> hash) { _expected = hash; }
    public void setContext(HashMap<String,String> hash) { _context = hash; }
    public void setProperties(HashMap<String,Object> hash) { _properties = hash; }
}
