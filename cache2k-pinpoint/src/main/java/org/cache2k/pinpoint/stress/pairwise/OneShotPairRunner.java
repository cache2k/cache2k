package org.cache2k.pinpoint.stress.pairwise;

/*
 * #%L
 * cache2k pinpoint
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs the actors in two different thread and synchronises the start
 * of the actors.
 *
 * @author Jens Wilke
 */
class OneShotPairRunner<R> {

  private final ActorPair<R> actorPair;
  private AtomicReference<R> result1 = new AtomicReference<R>();
  private AtomicReference<R> result2 = new AtomicReference<R>();
  private AtomicReference<Throwable> exception1 = new AtomicReference<Throwable>();
  private AtomicReference<Throwable> exception2 = new AtomicReference<Throwable>();

  public OneShotPairRunner(final ActorPair<R> actorPair) {
    this.actorPair = actorPair;
  }

  public void run(Executor executor) throws InterruptedException {
    actorPair.setup();
    exception1.set(null);
    exception2.set(null);
    final CountDownLatch startLatch = new CountDownLatch(2);
    final CountDownLatch finishLatch = new CountDownLatch(2);
    Runnable r1 = new Runnable() {
      @Override
      public void run() {
        try {
          startLatch.countDown();
          startLatch.await();
          result1.set(actorPair.actor1());
        } catch (Throwable t) {
          exception1.set(t);
        } finally {
          finishLatch.countDown();
        }
      }
    };
    Runnable r2 = new Runnable() {
      @Override
      public void run() {
        try {
          startLatch.countDown();
          startLatch.await();
          result2.set(actorPair.actor2());
        } catch (Throwable t) {
          exception2.set(t);
        } finally {
          finishLatch.countDown();
        }
      }
    };
    executor.execute(r1);
    executor.execute(r2);
    finishLatch.await();
    if (exception1.get() != null) {
      throw new ExceptionInActorThread(exception1.get());
    }
    if (exception2.get() != null) {
      throw new ExceptionInActorThread(exception2.get());
    }
    actorPair.check(result1.get(), result2.get());
  }

  static class ExceptionInActorThread extends AssertionError {

    public ExceptionInActorThread(final Throwable cause) {
      super(cause.toString(), cause);
    }
  }

}
