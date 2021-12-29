package org.cache2k.testsuite.expiry;

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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.expiry.ExpiryTimeValues;
import org.cache2k.testsuite.support.DataType;
import org.cache2k.testsuite.support.AbstractCacheTester;
import org.cache2k.testsuite.support.TestContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @see org.cache2k.testsuite.api.ExpiryApiTest
 * @author Jens Wilke
 */
public class ExpirySetupTest<K, V> extends AbstractCacheTester<K, V> {

  @Test
  public void defaults() {
    init(b -> {
      Cache2kConfig<K, V> cfg = b.config();
      assertFalse(cfg.isEternal());
      assertNull(cfg.getExpireAfterWrite());
    });
  }

  @Test
  public void maxExpireAfterWrite() {
    init(b -> {
      b.expireAfterWrite(Long.MAX_VALUE / 20, TimeUnit.MILLISECONDS);
    });
    put(k0, v0);
  }

	// at org.cache2k.core.timing.TimerWheels$Wheel.atNoon(TimerWheels.java:68)
  @Test
  public void beyondMaxExpireAfterWrite() {
    init(b -> {
      b.expireAfterWrite(Long.MAX_VALUE / 5, TimeUnit.MILLISECONDS);
    });
    assertThatCode(() -> put(k0, v0))
      .isInstanceOf(IllegalArgumentException.class);
  }

  /**
   * If eternal is set to true, no real expiry that needs the timer is
   * expected. Setting expiry works for NOW and ETERNAL
   */
  @Test
  public void eternal_true_setExpiryTime() {
    init(b -> b.eternal(true));
    invoke(k0, entry -> entry.setExpiryTime(123));
    put(k0, v0);
    invoke(k0, entry -> entry.setExpiryTime(ExpiryTimeValues.ETERNAL));
    invoke(k0, entry -> entry.setExpiryTime(ExpiryTimeValues.NOW));
    assertFalse(containsKey(k0));
    put(k0, v0);
    assertThatCode(() -> {
      invoke(k0, entry -> entry.setExpiryTime(123));
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void eternal_false_setExpiryTime() {
    init(b -> b.eternal(false));
    setExpiryComplete(Long.MAX_VALUE);
  }

  private void setExpiryComplete(long durationCapMillis) {
    invoke(k0, entry -> entry.setExpiryTime(123));
    put(k0, v0);
    invoke(k0, entry -> entry.setExpiryTime(ExpiryTimeValues.ETERNAL));
    invoke(k0, entry -> entry.setExpiryTime(ExpiryTimeValues.NOW));
    assertFalse(containsKey(k0));
    put(k0, v0);
    invoke(k0, entry -> entry.setExpiryTime(123));
    invoke(k0, entry -> entry.setValue(v0).setExpiryTime(TIME_MAX_MILLIS));
    long time = invoke(k0, entry -> entry.getExpiryTime());
    assertEquals(TIME_MAX_MILLIS, time);
    long delta = 123;
    within(delta)
      .expectMaybe(() -> {
        long t1 = now() + delta;
        invoke(k0, entry -> entry.setValue(v0).setExpiryTime(-t1));
        assertEquals(t1, (long) invoke(k0, entry -> entry.getExpiryTime()));
        invoke(k0, entry -> entry.setValue(v0).setExpiryTime(t1));
        assertEquals(t1, (long) invoke(k0, entry -> entry.getExpiryTime()));
      });
    invoke(k0, entry -> entry.setValue(v0).setExpiryTime(-TIME_MAX_MILLIS));
    assertEquals(TIME_MAX_MILLIS, (long) invoke(k0, entry -> entry.getExpiryTime()));
  }

  public static class ExpirySetupTestWithObjects extends ExpirySetupTest<Object, Object> {

    @Override
    protected TestContext<Object, Object> provideTestContext() {
      return new TestContext<>(DataType.OBJ_KEYS, DataType.OBJ_VALUES);
    }
  }

}
