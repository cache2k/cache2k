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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Executes the actor pairs in parallem for a configurable amount of time.
 * Any exception within the actor methods will be propagated.
 *
 * @author Jens Wilke
 * @see ActorPair
 */
public class ActorPairSuite {

  private final Set<ActorPair<?>> pairs = new HashSet<ActorPair<?>>();
  private final List<OneShotPairRunner> pairRunners = new ArrayList<OneShotPairRunner>();
  private final Map<OneShotPairRunner<?>, OneShotPairRunner<?>> running = new ConcurrentHashMap<OneShotPairRunner<?>, OneShotPairRunner<?>>();
  private final CountDownLatch finishLatch = new CountDownLatch(1);
  private final Random random = new Random(1802);
  private final List<Throwable> exceptions = new CopyOnWriteArrayList<Throwable>();
  private long finishTime;

  private int maxParallel = 2;
  private long runMillis = 2000;
  private Executor executor = Executors.newCachedThreadPool();
  private boolean stopAtFirstException = false;

  /**
   * Runtime in milliseconds. Default is 2 seconds. When set to 0, the
   * run stops immediately after running each pair once, up to a count of
   * maxParallel (used for testing). When set to {@link Long#MAX_VALUE} the
   * suite runs forever or until the first error occurs.
   */
  public ActorPairSuite runMillis(long v) {
    runMillis = v;
    return this;
  }

  /**
   * Maximum of pairs executed in parallel
   */
  public ActorPairSuite maxParallel(int v) {
    maxParallel = v;
    return this;
  }

  /**
   * Immediately stop at the first exception. The default is
   * not to stop and run for the preset runtime.
   */
  public ActorPairSuite stopAtFirstException(boolean v) {
    stopAtFirstException = v;
    return this;
  }

  /**
   * Add an actor pair
   */
  public ActorPairSuite addPair(ActorPair<?>... ps) {
    for (ActorPair<?> p : ps) {
      pairs.add(p);
    }
    return this;
  }

  /**
   * Alternative executor to run the tests.
   */
  public ActorPairSuite executor(Executor v) {
    executor = v;
    return this;
  }

  public void run() {
    if (pairs.isEmpty()) {
      throw new IllegalArgumentException("add actor pairs");
    }
    if (maxParallel < 1) {
      throw new IllegalArgumentException("maxParallel: " + maxParallel);
    }
    if (maxParallel > pairs.size()) {
      maxParallel = pairs.size();
    }
    finishTime =
    runMillis == Long.MAX_VALUE ? Long.MAX_VALUE :
      System.currentTimeMillis() + runMillis;
    for (ActorPair p : pairs) {
      pairRunners.add(new OneShotPairRunner<Object>(p));
    }
    for (int i = 0; i < maxParallel; i++) {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          runLoop();
        }
      });
    }
    try {
      finishLatch.await();
    } catch (InterruptedException ignore) { }
    checkForExceptions();
  }

  private void checkForExceptions() {
    if (!exceptions.isEmpty()) {
      throw new ActorPairSuiteError(exceptions.size(), exceptions.get(0));
    }
  }

  private OneShotPairRunner<?> nextRunner() {
    OneShotPairRunner<?> r;
    do {
      r = pairRunners.get(random.nextInt(pairRunners.size()));
    } while (running.putIfAbsent(r, r) != null);
    return r;
  }

  private void runLoop() {
    do {
      final OneShotPairRunner<?> runner = nextRunner();
      try {
        runner.run(executor);
      } catch (Throwable t) {
        exceptions.add(t);
      }
      running.remove(runner);
    } while (!stopRunning());
    if (running.isEmpty()) {
      finishLatch.countDown();
    }
  }

  private boolean stopRunning() {
    return (stopAtFirstException && !exceptions.isEmpty()) || (System.currentTimeMillis() >= finishTime);
  }

  public static class ActorPairSuiteError extends AssertionError {

    public ActorPairSuiteError(int numExceptions, Throwable firstException) {
      super(
        numExceptions + " errors in suite, first exception: " + firstException,
        firstException);
    }

  }

}
