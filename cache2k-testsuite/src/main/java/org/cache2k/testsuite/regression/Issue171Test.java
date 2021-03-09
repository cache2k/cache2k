package org.cache2k.testsuite.regression;

/*
 * #%L
 * cache2k testsuite on public API
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.event.CacheEntryRemovedListener;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Regression test for {@code computeIfAbsent} exception handling.
 *
 * @author Jens Wilke
 * @link <a href="https://github.com/cache2k/cache2k/issues/171"/>
 */
public class Issue171Test {

  @Test
  @Ignore("FIXME")
  public void testNpeWithoutListener() {
    testAndVerifyStacktrace(Cache2kBuilder.of(String.class, String.class)
      .build());
  }

  @Test
  @Ignore("FIXME")
  public void testNpeWithListener() {
    testAndVerifyStacktrace(Cache2kBuilder.of(String.class, String.class)
      .addListener((CacheEntryRemovedListener<String, String>) (cache1, cacheEntry) -> {
      })
      .build());
  }

  private static void testAndVerifyStacktrace(Cache<String, String> cache) {
    try {
      String value = null;
      cache.computeIfAbsent("npeKey", s -> value.substring(10));
      fail("this shall not pass");
    } catch (NullPointerException e) {
      assertEquals(e.getStackTrace()[0].getClassName(), Issue171Test.class.getName());
    }
  }

}
