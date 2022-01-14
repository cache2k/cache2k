package org.cache2k.test.core.expiry;

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

import org.cache2k.core.timing.TimingUnitTest;
import org.cache2k.test.util.TestingBase;
import org.cache2k.Cache;
import org.cache2k.test.core.TestingParameters;
import org.cache2k.testing.category.SlowTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * Test variants of cache mutators and check for correct expiry.
 *
 * @author Jens Wilke
 */
@Category(SlowTests.class) @RunWith(Parameterized.class)
public class AllMutatorsExpireTest extends TestingBase {

  static final long EXPIRY_BEYOND_GAP = TimingUnitTest.SHARP_EXPIRY_GAP_MILLIS + 3;
  static final Integer KEY = 1;
  static final Integer VALUE = 1;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    List<Pars> parameterList = new ArrayList<>();
    for (int variant = 0; variant <= 7; variant++) {
      for (long expiry : new long[]{0, TestingParameters.MINIMAL_TICK_MILLIS}) {
        for (boolean sharpExpiry : new boolean[]{false, true}) {
          for (boolean keepData :  new boolean[]{false, true}) {
            Pars p = new Pars();
            p.variant = variant;
            p.expiryDurationMillis = expiry;
            p.sharpExpiry = sharpExpiry;
            p.keepData = keepData;
            parameterList.add(p);
          }
        }
      }
    }
    for (int variant = 0; variant <= 7; variant++) {
      for (long expiry : new long[]{EXPIRY_BEYOND_GAP}) {
        for (boolean sharpExpiry : new boolean[]{true}) {
          for (boolean keepData :  new boolean[]{false}) {
            Pars p = new Pars();
            p.variant = variant;
            p.expiryDurationMillis = expiry;
            p.sharpExpiry = sharpExpiry;
            p.keepData = keepData;
            parameterList.add(p);
          }
        }
      }
    }
    return buildParameterCollection(parameterList);
  }

  static Collection<Object[]> buildParameterCollection(List<Pars> parameters) {
    List<Object[]> l = new ArrayList<>();
    for (Pars o : parameters) {
      l.add(new Object[]{o});
    }
    return l;
  }

  Pars pars;

  public AllMutatorsExpireTest(Pars p) { pars = p; }


  @Test
  public void test() {
    if (pars.sharpExpiry) {
      putExpiresSharply(pars.expiryDurationMillis, pars.variant, pars.keepData);
    } else {
      putExpiresLagging(pars.expiryDurationMillis, pars.variant, pars.keepData);
    }
  }

  void putExpiresSharply(long expiryTime, int variant, boolean keepData) {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .name(getAndSetCacheName("putExpiresSharply"))
      .expiryPolicy((key, value, startTime, currentEntry) -> startTime + expiryTime)
      .sharpExpiry(true)
      .keepDataAfterExpired(keepData)
      .build();
    AtomicInteger opCnt = new AtomicInteger();
    within(expiryTime)
      .perform(() -> opCnt.set(mutate(variant, c))).expectMaybe(() -> assertTrue(c.containsKey(1)));
    sleep(expiryTime);
    long laggingMillis = 0;
    if (c.containsKey(KEY)) {
      long t1 = ticks();
      await(() -> !c.containsKey(KEY));
      laggingMillis = ticks() - t1 + 1;
    }
    assertThat(c.containsKey(KEY)).isFalse();
    assertThat(c.peek(KEY)).isNull();
    assertThat(c.peekEntry(KEY)).isNull();
    if (opCnt.get() == 1) {
      await(() -> getInfo().getExpiredCount() == 1);
    }
    if (!pars.keepData && opCnt.get() == 1) {
      await(() -> getInfo().getSize() == 0);
    }
    assertThat(laggingMillis)
      .as("(flaky?) No lag, got delay: " + laggingMillis)
      .isEqualTo(0);
  }

  private String getAndSetCacheName(String methodName) {
    return cacheName = this.getClass().getSimpleName() + "-" + methodName + "-" +
      pars.toString().replace('=', '-');
  }

  void putExpiresLagging(long expiryTime, int variant, boolean keepData) {
    Cache<Integer, Integer> c = builder(Integer.class, Integer.class)
      .name(getAndSetCacheName("putExpiresLagging"))
      .expireAfterWrite(expiryTime, MILLISECONDS)
      .sharpExpiry(false)
      .keepDataAfterExpired(keepData)
      .build();
    AtomicInteger opCnt = new AtomicInteger();
    within(expiryTime)
      .perform(() -> opCnt.set(mutate(variant, c))).expectMaybe(() -> assertTrue(c.containsKey(1)));
    await(() -> !c.containsKey(KEY));
    assertThat(c.peek(KEY)).isNull();
    assertThat(c.peekEntry(KEY)).isNull();
    if (opCnt.get() == 1) {
      await(() -> getInfo().getExpiredCount() == 1);
    }
  }

  int mutate(int variant, Cache<Integer, Integer> c) {
    int opCnt = 1;
    switch (variant) {
      case 0:
        c.put(KEY, VALUE);
        break;
      case 1:
        c.putIfAbsent(KEY, VALUE);
        break;
      case 2:
        c.peekAndPut(KEY, VALUE);
        break;
      case 3:
        c.put(1, 1);
        c.peekAndReplace(KEY, VALUE);
        opCnt++;
        break;
      case 4:
        c.put(KEY, VALUE);
        c.replace(KEY, VALUE);
        opCnt++;
        break;
      case 5:
        c.put(KEY, VALUE);
        c.replaceIfEquals(KEY, VALUE, VALUE);
        opCnt++;
        break;
      case 6:
        c.invoke(KEY, e -> {
          e.setValue(VALUE);
          return null;
        });
        break;
      case 7:
        c.put(KEY, VALUE);
        c.put(KEY, VALUE);
        opCnt++;
        break;
    }
    return opCnt;
  }

  static class Pars {
    int variant;
    boolean sharpExpiry;
    long expiryDurationMillis;
    boolean keepData;

    @Override
    public String toString() {
      return
        "expiryDurationMillis=" + expiryDurationMillis +
        ",variant=" + variant +
        ",sharpExpiry=" + sharpExpiry +
        ",keepData=" + keepData;
    }
  }

}
