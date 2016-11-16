/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck;

import org.jsr107.tck.testutil.AllTestExcluder;
import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.CacheException;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.configuration.OptionalFeature;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Implementations can optionally support storeByReference.
 * <p>
 * Tests aspects where storeByReference makes a difference
 * </p>
 *
 * @author Yannis Cosmadopoulos
 * @author Greg Luck
 * @since 1.0
 */
public class StoreByReferenceTest extends CacheTestSupport<Date, Date> {
  /**
   * Rule used to exclude tests
   */
  @Rule
  public MethodRule rule =
      Caching.getCachingProvider().isSupported(OptionalFeature.STORE_BY_REFERENCE) ?
          new ExcludeListExcluder(this.getClass()) :
          new AllTestExcluder();

  @Before
  public void moreSetUp() {
    cache = getCacheManager().getCache(getTestCacheName(), Date.class, Date.class);
  }

  @Override
  protected MutableConfiguration<Date, Date> newMutableConfiguration() {
    return new MutableConfiguration<Date, Date>().setTypes(Date.class, Date.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected MutableConfiguration<Date, Date> extraSetup(MutableConfiguration<Date, Date> configuration) {
    return super.extraSetup(configuration).setStoreByValue(false);
  }

  @After
  public void teardown() {
    try {
      Caching.getCachingProvider().close();
    } catch (CacheException e) {
      //expected
    }
  }

  @Test
  public void get_Existing() {
    long now = System.currentTimeMillis();
    Date existingKey = new Date(now);
    Date existingValue = new Date(now);
    cache.put(existingKey, existingValue);
    // unnecessary since after we test for same (not equals), but "advertises" consequence
    existingValue.setTime(now + 1);
    assertSame(existingValue, cache.get(existingKey));
  }

  @Test
  public void get_Existing_NotSameKey() {
    long now = System.currentTimeMillis();
    Date existingKey = new Date(now);
    Date existingValue = new Date(now);
    cache.put(existingKey, existingValue);
    // unnecessary since after we test for same (not equals), but "advertises" consequence
    existingValue.setTime(now + 1);
    assertSame(existingValue, cache.get(new Date(now)));
  }


  @Test
  public void put_Existing_NotSameKey() throws Exception {
    long now = System.currentTimeMillis();
    Date key1 = new Date(now);
    Date value1 = new Date(now);
    cache.put(key1, value1);
    Date key2 = new Date(now);
    Date value2 = new Date(now);
    cache.put(key2, value2);
    // unnecessary since after we test for same (not equals), but "advertises" consequence
    value2.setTime(now + 1);
    assertSame(value2, cache.get(key2));
  }

  @Test
  public void getAndPut_NotThere() {
    long now = System.currentTimeMillis();
    Date existingKey = new Date(now);
    Date existingValue = new Date(now);
    assertNull(cache.getAndPut(existingKey, existingValue));
    // unnecessary since after we test for same (not equals), but "advertises" consequence
    existingValue.setTime(now + 1);
    assertSame(existingValue, cache.get(existingKey));
  }

  @Test
  public void getAndPut_Existing() {
    long now = System.currentTimeMillis();
    Date existingKey = new Date(now);
    Date value1 = new Date(now);
    cache.getAndPut(existingKey, value1);
    Date value2 = new Date(now + 1);
    assertSame(value1, cache.getAndPut(existingKey, value2));
    assertSame(value2, cache.get(existingKey));
  }

  @Test
  public void getAndPut_Existing_NotSameKey() {
    long now = System.currentTimeMillis();
    Date key1 = new Date(now);
    Date value1 = new Date(now);
    cache.getAndPut(key1, value1);
    Date key2 = new Date(now);
    Date value2 = new Date(now + 1);
    assertSame(value1, cache.getAndPut(key2, value2));
    assertSame(value2, cache.get(key1));
    assertSame(value2, cache.get(key2));
  }

  @Test
  public void putAll() {
    Map<Date, Date> data = createDDData(3);
    cache.putAll(data);
    for (Map.Entry<Date, Date> entry : data.entrySet()) {
      assertSame(entry.getValue(), cache.get(entry.getKey()));
    }
  }

  @Test
  public void putIfAbsent_Missing() {
    long now = System.currentTimeMillis();
    Date key = new Date(now);
    Date value = new Date(now);
    assertTrue(cache.putIfAbsent(key, value));
    assertSame(value, cache.get(key));
  }

  @Test
  public void putIfAbsent_There() {
    long now = System.currentTimeMillis();
    Date key = new Date(now);
    Date value = new Date(now);
    Date oldValue = new Date(now + 1);
    cache.put(key, oldValue);
    assertFalse(cache.putIfAbsent(key, value));
    assertSame(oldValue, cache.get(key));
  }

  @Test
  public void replace_3arg() throws Exception {
    long now = System.currentTimeMillis();
    Date key = new Date(now);
    Date value = new Date(now);
    cache.put(key, value);
    Date nextValue = new Date(now + 1);
    assertTrue(cache.replace(key, value, nextValue));
    assertSame(nextValue, cache.get(key));
  }

  @Test
  public void getAndReplace() {
    long now = System.currentTimeMillis();
    Date key = new Date(now);
    Date value = new Date(now);
    cache.put(key, value);
    Date nextValue = new Date(now + 1);
    assertSame(value, cache.getAndReplace(key, nextValue));
    assertSame(nextValue, cache.get(key));
  }
}
