package org.cache2k.extra.jmx;

/*-
 * #%L
 * cache2k JMX support
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.*;

/**
 * Test illegal characters in names.
 *
 * @author Jens Wilke
 */
@Category(FastTests.class) @RunWith(Parameterized.class)
public class IllegalNamesTest {

  private static final char[] ILLEGAL_CHARACTERS =
    new char[]{'"', '*', '{', '}', '[', ']' , ':', '=', '\\'};

  @Parameters
  public static Collection<Object[]> data() {
    ArrayList<Object[]> l = new ArrayList<Object[]>();
    for (char c : ILLEGAL_CHARACTERS) {
      l.add(new Object[]{c});
    }
    return l;
  }

  char aChar;

  public IllegalNamesTest(final char _aChar) {
    aChar = _aChar;
  }

  @Test
  public void testCache() {
    try {
      Cache c = Cache2kBuilder.forUnknownTypes()
        .name(IllegalNamesTest.class.getName() + "-test-with-char-" + aChar)
        .build();
      c.close();
      fail("expected exception for cache name with " + aChar);
    } catch (IllegalArgumentException expected) {
      /* expected */
    }

  }

  @Test
  public void testManager() {
    try {
      CacheManager cm = CacheManager.getInstance(IllegalNamesTest.class.getName() + "-char-" + aChar);
      cm.close();
    } catch (IllegalArgumentException expected) {
       /* expected */
    }
  }

}
