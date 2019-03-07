package org.cache2k.impl.serverSide;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.management.MBeanInfo;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.fail;

import static org.junit.Assert.*;

/**
 * Test legal characters in names.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class) @RunWith(Parameterized.class)
public class LegalNamesTest {

  private static final String LEGAL_CHARACTERS =
    ",-()~_.+!'%#";

  @Parameters
  public static Collection<Object[]> data() {
    ArrayList<Object[]> l = new ArrayList<Object[]>();
    for (char c : LEGAL_CHARACTERS.toCharArray()) {
      l.add(new Object[]{c});
    }
    return l;
  }

  private char aChar;

  public LegalNamesTest(final char _aChar) {
    aChar = _aChar;
  }

  @Test
  public void testCache() throws Exception {
    String _name = LegalNamesTest.class.getName() + "-test-with-char-" + aChar;
    Cache c = Cache2kBuilder.forUnknownTypes()
      .name(_name)
      .enableJmx(true)
      .build();
    assertEquals("default", c.getCacheManager().getName());
    assertTrue(c.getCacheManager().isDefaultManager());
    assertEquals(_name, c.getName());
    MBeanInfo inf = JmxSupportTest.getCacheManagerInfo(c.getCacheManager().getName());
    assertNotNull(inf);
    inf = JmxSupportTest.getCacheInfo(c.getName());
    assertNotNull(inf);
    c.close();
  }

  @Test
  public void testManager() throws Exception {
    CacheManager cm = CacheManager.getInstance(LegalNamesTest.class.getName() + "-char-" + aChar);
    MBeanInfo inf = JmxSupportTest.getCacheManagerInfo(cm.getName());
    assertNotNull(inf);
    Cache2kBuilder.forUnknownTypes().manager(cm).name("dummy").build();
    cm.close();
  }

}
