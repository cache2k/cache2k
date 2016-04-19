package org.cache2k.core.util;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static org.junit.Assert.*;

import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke; created: 2015-03-30
 */
@Category(FastTests.class)
public class ConfigurationReaderTest {

  @Test
  public void testInteger() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("intvalue", "1234");
    assertEquals(1234, cfg.getIntValue());
  }

  @Test
  public void testLong() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("longvalue", "1234");
    assertEquals(1234, cfg.getLongValue());
  }

  @Test
  public void testBoolean() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("booleanvalue", "true");
    assertEquals(true, cfg.isBooleanValue());
    r.apply("booleanvalue", "false");
    assertEquals(false, cfg.isBooleanValue());
    r.apply("booleanvalue", "yes");
    assertEquals(true, cfg.isBooleanValue());
  }

  @Test
  public void testTimeSeconds() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("time", "1234s");
    assertEquals(1234 * 1000, cfg.getTimeMillis());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTimeWrongSuffix() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("time", "1234bla");
    fail();
  }

  @Test
  public void testType() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("type", String.class.getName());
    assertEquals(String.class, cfg.getType());
  }

  @Test
  public void testString() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("someString", "hello");
    assertEquals("hello", cfg.getSomeString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUnsupportedSetter() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("unsupported", "1234bla");
    fail();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUnknownType() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("unknownType", "1234bla");
    fail();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testUnknownParameter() throws Exception {
    SomeConfig cfg = new SomeConfig();
    ConfigurationReader r = new ConfigurationReader(cfg);
    r.apply("unknownParameter", "1234bla");
    fail();
  }

  public static class SomeConfig {

    int intValue;
    long longValue;
    boolean booleanValue;
    long time;
    String someString;
    Class<?> type;

    public int getIntValue() {
      return intValue;
    }

    public void setIntValue(int intValue) {
      this.intValue = intValue;
    }

    public long getLongValue() {
      return longValue;
    }

    public void setLongValue(long longValue) {
      this.longValue = longValue;
    }

    public boolean isBooleanValue() {
      return booleanValue;
    }

    public void setBooleanValue(boolean booleanValue) {
      this.booleanValue = booleanValue;
    }

    public long getTimeMillis() {
      return time;
    }

    public void setTime(long _duration, TimeUnit _unit) {
      time = _unit.toMillis(_duration);
    }

    public void setTimeMillis(long time) {
      this.time = time;
    }

    public String getSomeString() {
      return someString;
    }

    public void setSomeString(String someString) {
      this.someString = someString;
    }

    public Class<?> getType() {
      return type;
    }

    public void setType(Class<?> type) {
      this.type = type;
    }

    public void setUnsupported(int x, int y) { }

    public void setUnknownType(SomeConfig cfg) { }

  }

}
