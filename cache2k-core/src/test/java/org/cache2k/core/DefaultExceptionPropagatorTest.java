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

import static java.lang.Long.MAX_VALUE;
import static java.lang.System.currentTimeMillis;
import static org.assertj.core.api.Assertions.assertThat;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ExceptionPropagator;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Check all variants the standard propagator supports
 *
 * @author Jens Wilke
 * @see DefaultExceptionPropagator
 */
@SuppressWarnings("unchecked")
public class DefaultExceptionPropagatorTest {

  static final ExceptionPropagator DEFAULT_PROPAGATOR = DefaultExceptionPropagator.SINGLETON;
  static final String TIME_STRING = "2016-05-25T09:30:12.123";
  static final long SOME_TIME = LocalDateTime.parse(TIME_STRING)
    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

  @Test
  public void propagate_eternal() {
    currentTimeMillis();
    RuntimeException t = DEFAULT_PROPAGATOR.propagateException(
      toInfo(new RuntimeException("serious thing"), MAX_VALUE));
    assertThat(t.toString().contains("expiry=ETERNAL")).isTrue();
  }

  @Test
  public void propagate_sometime() {
    RuntimeException t = DEFAULT_PROPAGATOR.propagateException(
      toInfo(new RuntimeException("serious thing"), SOME_TIME));
    assertThat(t.toString()).contains(("expiry=2016-05-25T09:30:12.123"));
  }

  @Test
  public void propagate_notime() {
    RuntimeException t = DEFAULT_PROPAGATOR.propagateException(
      toInfo(new RuntimeException("serious thing"), 0));
    assertThat(t.toString().contains("expiry=")).isFalse();
  }

  private LoadExceptionInfo toInfo(Throwable ex, long t) {
    return new LoadExceptionInfo() {
      @Override
      public Object getKey() { return null; }
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
