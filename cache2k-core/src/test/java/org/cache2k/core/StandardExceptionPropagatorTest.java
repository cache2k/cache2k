package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ExceptionPropagator;
import org.junit.Test;

import java.sql.Timestamp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Check all variants the standard propagator supports
 *
 * @author Jens Wilke
 * @see StandardExceptionPropagator
 */
@SuppressWarnings("unchecked")
public class StandardExceptionPropagatorTest {

  static final ExceptionPropagator STANDARD_PROPAGATOR = new StandardExceptionPropagator();
  static final String TIME_STRING = "2016-05-25 09:30:12.123";
  static final long SOME_TIME = Timestamp.valueOf(TIME_STRING).getTime();

  @Test
  public void propagate_eternal() {
    System.currentTimeMillis();
    RuntimeException t = STANDARD_PROPAGATOR.propagateException(
      null, toInfo(new RuntimeException("serious thing"), Long.MAX_VALUE));
    assertTrue(t.toString().contains("expiry=ETERNAL"));
  }

  @Test
  public void propagate_sometime() {
    RuntimeException t = STANDARD_PROPAGATOR.propagateException(
      null, toInfo(new RuntimeException("serious thing"), SOME_TIME));
    assertTrue(t.toString().contains("expiry=2016-05-25 09:30:12.123"));
  }

  @Test
  public void propagate_notime() {
    RuntimeException t = STANDARD_PROPAGATOR.propagateException(
      null, toInfo(new RuntimeException("serious thing"), 0));
    assertFalse(t.toString().contains("expiry="));
  }

  private ExceptionInformation toInfo(final Throwable ex, final long t) {
    return new ExceptionInformation() {
      @Override
      public ExceptionPropagator getExceptionPropagator() { return null; }

      @Override
      public Throwable getException() {
        return ex;
      }

      @Override
      public int getRetryCount() {
        return 0;
      }

      @Override
      public long getSinceTime() {
        return 0;
      }

      @Override
      public long getLoadTime() {
        return 0;
      }

      @Override
      public long getUntil() {
        return t;
      }
    };
  }

}
