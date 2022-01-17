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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
public class SupervisedExecutorTest {

  @Test
  public void all() {
    SupervisedExecutor ex = new SupervisedExecutor(ForkJoinPool.commonPool());
    ex.join();
    AtomicBoolean executed = new AtomicBoolean(false);
    ex.execute(() -> executed.set(true));
    ex.join();
    assertThat(executed.get()).isTrue();
    ex.execute(() -> {
      throw new ExpectedException();
    });
    assertThatCode(() -> ex.close())
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("No exception expected, 1 exceptions, first one propagated");
  }

  @Test
  public void timeout() {
    SupervisedExecutor ex = new SupervisedExecutor(command -> {}, Duration.ZERO);
    ex.execute(() -> {});
    assertThatCode(() -> ex.join())
      .isInstanceOf(TimeoutError.class);
  }

  @Test
  public void interruption() {
    Semaphore semaphore = new Semaphore(1);
    Thread.currentThread().interrupt();
    assertThatCode(() -> SupervisedExecutor.acquireOrTimeout(
      semaphore, Duration.ZERO))
      .isInstanceOf(CaughtInterruptedExceptionError.class);
    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }

  @Test
  public void timeoutSemaphore() {
    Semaphore semaphore = new Semaphore(1);
    SupervisedExecutor.acquireOrTimeout(semaphore, Duration.ZERO);
    assertThatCode(() -> SupervisedExecutor.acquireOrTimeout(semaphore, Duration.ZERO))
      .isInstanceOf(TimeoutError.class)
      .hasMessageContaining("Timeout after 0 seconds");
    assertThat(Thread.currentThread().isInterrupted()).isFalse();
  }

}
