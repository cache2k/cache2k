package org.cache2k.pinpoint.stress.pairwise;

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

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the actors in two different thread and synchronises the start
 * of the actors.
 *
 * @author Jens Wilke
 */
class OneShotPairRunner<R1, R2> {

  private final ActorPair<R1, R2> actorPair;
  private final AtomicReference<R1> result1 = new AtomicReference<>();
  private final AtomicReference<R2> result2 = new AtomicReference<>();
  private final AtomicReference<Throwable> exception1 = new AtomicReference<>();
  private final AtomicReference<Throwable> exception2 = new AtomicReference<>();
  private final AtomicReference<Throwable> observerException = new AtomicReference<>();
  private final AtomicInteger finishLatch = new AtomicInteger();

  OneShotPairRunner(ActorPair<R1, R2> actorPair) {
    this.actorPair = actorPair;
  }

  public void run(Executor executor) throws InterruptedException {
    actorPair.setup();
    exception1.set(null);
    exception2.set(null);
    boolean observerPresent = observerPresent(actorPair.getClass());
    finishLatch.set(observerPresent ? 3 : 2);
    CountDownLatch startLatch = new CountDownLatch(finishLatch.get());
    Runnable r1 = () -> {
      try {
        startLatch.countDown();
        startLatch.await();
        result1.set(actorPair.actor1());
      } catch (Throwable t) {
        exception1.set(t);
      } finally {
        finishLatch.decrementAndGet();
      }
    };
    Runnable r2 = () -> {
      try {
        startLatch.countDown();
        startLatch.await();
        result2.set(actorPair.actor2());
      } catch (Throwable t) {
        exception2.set(t);
      } finally {
        finishLatch.decrementAndGet();
      }
    };
    if (observerPresent) {
      Runnable r3 = () -> {
        try {
          startLatch.countDown();
          startLatch.await();
          actorPair.observe();
        } catch (Throwable t) {
          observerException.set(t);
        } finally {
          finishLatch.decrementAndGet();
        }
      };
      executor.execute(r3);
    }
    if ((this.hashCode() & 1) == 0) {
      Runnable tmp = r1; r1 = r2; r2 = tmp;
    }
    executor.execute(r1);
    executor.execute(r2);
    while(finishLatch.get() > 0) { }
    if (exception1.get() != null) {
      throw new ActorException(exception1.get());
    }
    if (exception2.get() != null) {
      throw new ActorException(exception2.get());
    }
    if (observerException.get() != null) {
      throw new ObserverException(observerException.get());
    }
    actorPair.check(result1.get(), result2.get());
  }

  static boolean observerPresent(Class<?> clazz) {
    try {
      Method m = clazz.getMethod("observe");
      return ! m.getDeclaringClass().equals(ActorPair.class);
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  static class ActorException extends AssertionError {

    ActorException(Throwable cause) {
      super(cause);
    }

  }

  static class ObserverException extends AssertionError {

    ObserverException(Throwable cause) {
      super(cause);
    }

  }

}
