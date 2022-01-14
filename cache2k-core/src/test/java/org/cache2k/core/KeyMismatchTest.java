package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
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
import org.cache2k.pinpoint.ExpectedException;
import org.cache2k.test.util.CacheSourceForTesting;
import org.cache2k.test.util.TestingBase;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.core.log.Log.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class KeyMismatchTest extends TestingBase {

  @Test
  public void testKeyMismatch() {
    String name = this.getClass().getName() + ".testKeyMismatch";
    SuppressionCounter suppressionCounter = new SuppressionCounter();
    String suppressionName = "org.cache2k.Cache/default:" + name;
    registerSuppression(suppressionName, suppressionCounter);
    IntWrapperCacheSource g = new IntWrapperCacheSource();
    Cache<IntWrapper, Integer> c =
      builder(IntWrapper.class, Integer.class)
        .loader(g)
        .entryCapacity(1)
        .name(name)
        .expireAfterWrite(5 * 60, SECONDS)
        .build();
    cacheName = name;
    IntWrapper k = new IntWrapper(77);
    int v = c.get(k);
    assertThat(v).isEqualTo(k.getSomeInt());
    assertThat(g.getLoaderCalledCount()).isEqualTo(1);
    checkIntegrity();
    k.changeSomeInt(27);
    checkIntegrity();
    c.put(new IntWrapper(4711), 1);
    c.get(new IntWrapper(4711));
    checkIntegrity();
    assertThat(suppressionCounter.getWarnCount()).isEqualTo(2);
    deregisterSuppression(suppressionName);
  }

  @Test
  public void testKeyMismatchLooping() {
    String name = this.getClass().getName() + ".testKeyMismatchLooping";
    SuppressionCounter suppressionCounter = new SuppressionCounter();
    String suppressionName = "org.cache2k.Cache/default:" + name;
    registerSuppression(suppressionName, suppressionCounter);
    IntWrapperCacheSource g = new IntWrapperCacheSource();
    Cache<IntWrapper, Integer> c =
      builder(IntWrapper.class, Integer.class)
      .loader(g)
      .entryCapacity(1)
      .name(name)
      .expireAfterWrite(5 * 60, SECONDS).build();
    cacheName = name;
    for (int i = 0; i < 5; i++) {
      int missBase = g.getLoaderCalledCount();
      IntWrapper k = new IntWrapper(77);
      c.get(k);
      int v = c.get(k);
      assertThat(v).isEqualTo(k.getSomeInt());
      if (missBase + 1 != g.getLoaderCalledCount()) {
        c.get(k);
        missBase++;
      }
      assertThat(g.getLoaderCalledCount()).isEqualTo(missBase + 1);
      checkIntegrity();
      k.changeSomeInt(27);
      checkIntegrity();
      v = c.get(k);
      assertThat(v).isEqualTo(k.getSomeInt());
      assertThat(g.getLoaderCalledCount()).isEqualTo(missBase + 2);
      checkIntegrity();
    }
    assertThat(suppressionCounter.getWarnCount()).isEqualTo(2);
    deregisterSuppression(suppressionName);
  }

  @Test
  public void testToStringException() {
    Object defunctKey = new Object() {
      @Override
      public String toString() {
        throw new ExpectedException();
      }
    };
    final SuppressionCounter log = new SuppressionCounter();
    HeapCache.logKeyMutation(log, defunctKey);
    assertThat(log.getWarnCount()).isEqualTo(2);
  }

  static class IntWrapperCacheSource extends CacheSourceForTesting<IntWrapper, Integer> {

    @Override
    public Integer load(IntWrapper o) {
      incrementLoadCalledCount();
      return o.getSomeInt();
    }

  }

  static class IntWrapper {
    int someInt;

    IntWrapper(int v) {
      this.someInt = v;
    }

    public void changeSomeInt(int i) {
      someInt = i;
    }

    public int getSomeInt() {
      return someInt;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      IntWrapper that = (IntWrapper) o;
      return someInt == that.someInt;
    }

    @Override
    public int hashCode() {
      return someInt;
    }

    @Override
    public String toString() {
      return "IntWrapper{" +
        "someInt=" + someInt +
        '}';
    }
  }

}
