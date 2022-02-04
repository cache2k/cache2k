package org.cache2k.pinpoint;

/*-
 * #%L
 * cache2k pinpoint
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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
public class AwaitTest {

  Await.YieldCpu yield = () -> { };

  @Test
  public void okay() {
    new Await(yield).await(() -> true);
    AtomicInteger count = new AtomicInteger(2);
    new Await(yield).await(() -> count.decrementAndGet() == 0);
  }

  @Test
  public void timeout() {
    assertThatCode(() -> {
      new Await(yield).await("text", Duration.ZERO, () -> false);
    }).isInstanceOf(TimeoutError.class)
      .hasMessageContaining("text");
    assertThatCode(() -> {
      new Await(yield).await(null, Duration.ZERO, () -> false);
    }).isInstanceOf(TimeoutError.class);
  }

  @Test
  public void interrupted() {
    Await.YieldCpu yield = () -> {
      throw new InterruptedException();
    };
    assertThatCode(() -> {
      new Await(yield).await("text", PinpointParameters.TIMEOUT, () -> false);
    }).isInstanceOf(CaughtInterruptedExceptionError.class);
  }

}
