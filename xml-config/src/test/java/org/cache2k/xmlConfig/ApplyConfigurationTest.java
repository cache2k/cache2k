package org.cache2k.xmlConfig;

/*
 * #%L
 * cache2k XML configuration
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

import org.cache2k.configuration.CacheConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Read in some configuration from a file.
 *
 * @author Jens Wilke
 * @see ApplyConfiguration
 */
public class ApplyConfigurationTest {

  @Test
  public void testGenerateSetterMap_TestConfig() {
    Map<String, Method> map = ApplyConfiguration.generateSetterLookupMap(TestConfig.class);
    assertTrue(map.containsKey("hello"));
    assertTrue(map.containsKey("intValue"));
    assertEquals(4, map.size());
  }

  @Test
  public void testIntType() throws Exception {
    Method m = TestConfig.class.getMethod("setIntValue", Integer.TYPE);
    assertNotNull(m);
  }

  @Test
  public void testGenerateSetterMap_CacheConfig() {
    Map<String, Method> map = ApplyConfiguration.generateSetterLookupMap(CacheConfiguration.class);
    assertTrue(map.containsKey("entryCapacity"));
  }

  @Test
  public void testParseSampleXml() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/cache2k-cache-sample.xml");
    ConfigPullParser pp = new XppConfigParser(is);
    CacheConfiguration cfg = new CacheConfiguration();
    ApplyConfiguration apl = new ApplyConfiguration(cfg, pp);
    apl.parse();
    assertEquals(5, cfg.getEntryCapacity());
    assertEquals(true, cfg.isSharpExpiry());
    assertEquals(false, cfg.isSuppressExceptions());
  }

  @Test
  public void testParseTestConfig() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/test-config.xml");
    ConfigPullParser pp = new XppConfigParser(is);
    TestConfig cfg = new TestConfig();
    ApplyConfiguration apl = new ApplyConfiguration(cfg, pp);
    apl.parse();
    assertEquals(4711, cfg.getIntValue());
    assertEquals(5432, cfg.getLongValue());
    assertEquals(true, cfg.isBooleanValue());
  }

  @SuppressWarnings("unused")
  public static class TestConfig {

    String hello;
    int intValue;
    long longValue;
    boolean booleanValue;

    public boolean isBooleanValue() {
      return booleanValue;
    }

    public void setBooleanValue(final boolean _booleanValue) {
      booleanValue = _booleanValue;
    }

    public long getLongValue() {
      return longValue;
    }

    public void setLongValue(final long _longValue) {
      longValue = _longValue;
    }

    public String getHello() {
      return hello;
    }

    public void setHello(final String _hello) {
      hello = _hello;
    }

    public int getIntValue() {
      return intValue;
    }

    public void setIntValue(final int _intValue) {
      intValue = _intValue;
    }

  }

}
