package org.cache2k.xmlConfig;

/*
 * #%L
 * cache2k XML configuration
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.CacheConfig;
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
    assertEquals(2, map.size());
  }

  @Test
  public void testIntType() throws Exception {
    Method m = TestConfig.class.getMethod("setIntValue", Integer.TYPE);
    assertNotNull(m);
  }

  @Test
  public void testGenerateSetterMap_CacheConfig() {
    Map<String, Method> map = ApplyConfiguration.generateSetterLookupMap(CacheConfig.class);
    assertTrue(map.containsKey("entryCapacity"));
  }

  @Test
  public void testParseSampleXml() throws Exception {
    InputStream is = this.getClass().getResourceAsStream("/cache2k-cache-sample.xml");
    ConfigPullParser pp = new XppConfigParser(is);
    CacheConfig cfg = new CacheConfig();
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
