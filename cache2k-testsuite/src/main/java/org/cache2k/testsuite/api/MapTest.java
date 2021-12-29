package org.cache2k.testsuite.api;

/*-
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

import java.util.Map;
import java.util.function.Supplier;

import com.google.common.collect.testing.MapTestSuiteBuilder;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;

import com.google.common.collect.testing.ConcurrentMapTestSuiteBuilder;
import com.google.common.collect.testing.TestStringMapGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.testing.category.FastTests;
import org.junit.experimental.categories.Category;

/**
 * Test details of {@link org.cache2k.Cache#asMap}. Contributed by Ben Manes.
 *
 * @see <a href="https://github.com/cache2k/cache2k/issues/174">Github issue #174</a>
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class MapTest extends TestCase {

  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(suite("cache2k_disallowNull", false, () -> {
      return new Cache2kBuilder<String, String>() {};
    }));
    suite.addTest(suite("cache2k_allowNull", true, () -> {
      return new Cache2kBuilder<String, String>() {};
    }));
    suite.addTest(suite("cache2k_wired_disallowNull", false, () -> {
      return new Cache2kBuilder<String, String>() {}.setup(MapTest::enforceWired);
    }));
    suite.addTest(suite("cache2k_wired_allowNull", true, () -> {
      return new Cache2kBuilder<String, String>() {}.setup(MapTest::enforceWired);
    }));
    return suite;
  }

  /**
   * Add a listener to enforce wired cache implementation.
   */
  private static void enforceWired(Cache2kBuilder<?, ?> builder) {
    builder.addListener((CacheEntryRemovedListener) (cache, entry) -> { });
  }

  private static Test suite(String name, boolean permitNullValues,
                            Supplier<Cache2kBuilder<String, String>> supplier) {
    MapTestSuiteBuilder<String, String> suite =
      ConcurrentMapTestSuiteBuilder.using(new TestStringMapGenerator() {
          @Override protected Map<String, String> create(Map.Entry<String, String>[] entries) {
            Map<String, String> map = supplier.get().permitNullValues(permitNullValues).build().asMap();
            for (Map.Entry<String, String> entry : entries) {
              map.put(entry.getKey(), entry.getValue());
            }
            return map;
          }
        })
        .named(name)
        .withFeatures(
            MapFeature.GENERAL_PURPOSE,
            MapFeature.ALLOWS_NULL_ENTRY_QUERIES,
            CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
            CollectionSize.ANY);
    if (permitNullValues) {
      suite.withFeatures(MapFeature.ALLOWS_NULL_VALUES);
    }
    return suite.createTestSuite();
  }

}
