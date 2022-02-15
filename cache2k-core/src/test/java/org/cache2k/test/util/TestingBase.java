package org.cache2k.test.util;

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
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CacheManager;
import org.cache2k.core.HeapCache;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.core.WiredCache;
import org.cache2k.core.concurrency.ThreadFactoryProvider;
import org.cache2k.core.timing.TimingUnitTest;
import org.cache2k.operation.Scheduler;
import org.cache2k.operation.TimeReference;
import org.cache2k.pinpoint.TimeBox;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.io.CacheLoader;
import org.cache2k.pinpoint.SupervisedExecutor;
import org.cache2k.test.core.Statistics;
import org.cache2k.test.core.TestingParameters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.CacheManager.getInstance;

/**
 * Base class for most of the cache tests. Provides a separate cache for
 * each test within the default cache manager. Provides methods for testing the timing
 * like {@link #ticks()}, {@link #within(long)} and {@link #sleep(long)}, that may be
 * backed via a simulated clock implementation {@link #enableFastClock()}.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class TestingBase {

  public static final int DEFAULT_MAX_SIZE = 200;

  public static final int MINIMAL_LOADER_THREADS = 4;

  private static final ThreadLocal<SimulatedClock> THREAD_CLOCK =
    ThreadLocal.withInitial(() -> new SimulatedClock(1000000));

  private static final AtomicLong UNIQUE_NAME_COUNTER = new AtomicLong();

  /**
   * Separate thread pool for testing. The thread pool is limited, so we know when some tests
   * use an unexpected high amount of threads. The number of available threads should be
   * changed with the number of parallelism we use for running the test. See {@code pom.xml}.
   */
  public static final Executor SHARED_EXECUTOR =
    new ThreadPoolExecutor(4, 8 * Runtime.getRuntime().availableProcessors(),
      21, TimeUnit.SECONDS,
      new SynchronousQueue<>(),
      ThreadFactoryProvider.DEFAULT.newThreadFactory("test-loader-pool"),
      new ThreadPoolExecutor.AbortPolicy());

  private Executor loaderExecutor = new ExecutorWrapper();
  private Executor asyncExecutor = ForkJoinPool.commonPool();

  public Executor getLoaderExecutor() {
    return loaderExecutor;
  }

  private static final TimeStepper DEFAULT_TIME_STEPPER = new TimeStepper(TimeReference.DEFAULT);
  private TimeStepper stepper = DEFAULT_TIME_STEPPER;
  private TimeReference clock = TimeReference.DEFAULT;
  private Statistics statistics;

  @Rule
  public TestRule checkAndCleanup =
    RuleChain.outerRule(
    new ExternalResource() {
      @Override
      protected void after() {
        provideOptionalCache();
        if (cache != null) {
          cleanup();
        }
        }
    }
    ).around(new TestRule() {
             @Override
             public Statement apply(Statement base, Description description) {
               return new Statement() {
                 @Override
                 public void evaluate() throws Throwable {
                   try {
                     base.evaluate();
                     provideOptionalCache();
                     if (cache != null) {
                       TestingBase.this.verify();
                     }
                     resetClock();
                   } catch (Throwable t) {
                     provideOptionalCache();
                     if (cache != null) {
                       try {
                         System.err.println("loaderExecutor=" + loaderExecutor);
                         System.err.println("asyncExecutor=" + asyncExecutor);
                         System.err.println(clock);
                         System.err.println(getInfo());
                       } catch (Throwable _getInfoException) {
                         System.err.println("Cannot print info");
                         _getInfoException.printStackTrace();
                       }
                     }
                     throw t;
                   }
                 }
               };
             }
           }
    );

  protected String cacheName;
  protected Cache cache;


  public void setClock(TimeReference c) {
    if (clock != TimeReference.DEFAULT) {
      throw new IllegalArgumentException("clock already set");
    }
    clock = c;
    stepper = new TimeStepper(c);
  }

  public void enableFastClock() {
    if (System.getProperty("disableSimulatedClock") != null) {
      return;
    }
    SimulatedClock c = THREAD_CLOCK.get();
    loaderExecutor = c.wrapExecutor(loaderExecutor);
    asyncExecutor = c.wrapExecutor(asyncExecutor);
    setClock(c);
  }

  /**
   * Make sure clock instance can be reused
   */
  public void resetClock() {
    if (clock instanceof SimulatedClock) {
      ((SimulatedClock) clock).reset();
    }
  }

  /**
   * Construct a simple build with int types.
   */
  protected Cache2kBuilder<Integer, Integer> builder() {
    return builder(Integer.class, Integer.class);
  }

  protected <K, T> Cache2kBuilder<K, T> builder(Class<K> k, Class<T> t) {
    return builder(generateUniqueCacheName(this), k, t);
  }

  protected <K, T> Cache2kBuilder<K, T> builder(String cacheName, Class<K> k, Class<T> t) {
    provideCache();
    if (cache != null) {
      checkIntegrity();
      cache.clear();
      cache.close();
      cache = null;
    }
    this.cacheName = cacheName;
    Cache2kBuilder<K, T> b = Cache2kBuilder.of(k, t)
      .name(cacheName)
      .entryCapacity(DEFAULT_MAX_SIZE)
      .timerLag(TestingParameters.MINIMAL_TICK_MILLIS / 2, TimeUnit.MILLISECONDS)
      .loaderExecutor(loaderExecutor)
      .executor(asyncExecutor);
    if (clock != TimeReference.DEFAULT) {
      b.timeReference(clock);
      if (clock instanceof Scheduler) {
        b.scheduler((Scheduler) clock);
      }
    }
    applyAdditionalOptions(b);
    return b;
  }

  /**
   *
   * @param b
   */
  protected void applyAdditionalOptions(Cache2kBuilder b) { }

  protected void applyMaxElements(Cache2kBuilder b, long maxElements) {
    b.entryCapacity(maxElements);
  }

  protected <K, T> Cache<K, T> freshCache(
      Class<K> keyClass, Class<T> dataClass, CacheLoader g, long maxElements, int expiry) {
    Cache2kBuilder<K, T> b =
      builder(keyClass, dataClass)
        .setup(x -> { if (g != null) x.loader(g); })
        .refreshAhead(expiry >= 0 && g != null);
    if (expiry < 0) {
      b.eternal(true);
    } else {
      b.expireAfterWrite(expiry, TimeUnit.SECONDS);
    }
    applyMaxElements(b, maxElements);
    return cache = b.build();
  }

  protected Cache<Integer, Integer> freshCache(CacheLoader<Integer, Integer> g,
                                               long maxElements, int expirySeconds) {
    return freshCache(Integer.class, Integer.class, g, maxElements, expirySeconds);
  }

  protected Cache<Integer, Integer> freshCache(CacheLoader<Integer, Integer> g, long maxElements) {
    return freshCache(g, maxElements, 5 * 60);
  }

  public void cleanup() {
    try {
      boolean debug = false;
      if (debug) {
        System.err.println("tearDown: " + cache);
        if (cache instanceof InternalCache) {
          InternalCache bc = (InternalCache) cache;
        }
      }
      if (cache != null && !cache.isClosed()) {
        cache.clear();
        verify();
        cache.close();
      }
      resetClock();
    } catch (Throwable ex) {
      ex.printStackTrace();
    }
    cache = null;
    cacheName = null;
  }

  /**
   * Extract the count from the cache or info if available. 0 since feature
   * is off.
   */
  public int statLoadsInFlight(Cache c, InternalCacheInfo inf) {
    return 0;
  }

  public void verify() {
    InternalCacheInfo inf = getInfo();
    assertThat(statLoadsInFlight(cache, inf))
      .as("fetchesInFlight == 0")
      .isEqualTo(0);
    assertThat(inf.getInternalExceptionCount())
      .as("exception count = 0")
      .isEqualTo(0);
    assertThat(cache)
      .as("cache was tested")
      .isNotNull();
    if (cache instanceof InternalCache) {
      ((InternalCache) cache).checkIntegrity();
    }
  }

  protected Statistics statistics() {
    if (statistics == null) {
      statistics = new Statistics();
    }
    return statistics.sample(cache);
  }

  protected void provideOptionalCache() {
    if (cacheName != null) {
      if (cache == null) {
        CacheManager cm = CacheManager.getInstance();
        cache = cm.getCache(cacheName);
      } else {
        assertThat(cache.isClosed())
          .as("cache is not closed")
          .isFalse();
      }
    }
  }

  protected void closeCache() {
    provideCache();
    cache.close();
    cacheName = null;
    cache = null;
  }

  /**
   * Expect a non closed cache is available
   */
  protected void provideCache() {
    if (cacheName != null) {
      if (cache == null) {
        CacheManager cm = getInstance();
        cache = cm.getCache(cacheName);
      }
      assertThat(cache)
        .as("cache is available")
        .isNotNull();
      assertThat(cache.isClosed())
        .as("cache is not closed")
        .isFalse();
    }
  }

  protected void checkIntegrity() {
    provideCache();
    if (cache instanceof InternalCache) {
      ((InternalCache) cache).checkIntegrity();
    }
  }

  protected Cache getCache() {
    provideCache();
    return cache;
  }

  protected InternalCache getInternalCache() {
    provideCache();
    return (InternalCache) cache;
  }

  protected void printStats() {
    System.err.println(getInfo());
  }

  protected InternalCacheInfo getInfo() {
    return getInternalCache().getConsistentInfo();
  }

  protected void debugEntry(Object key) {
    System.out.println(getInternalCache().getEntryState(key));
  }

  protected void drainEvictionQueue() {
    getInfo();
  }

  protected void drainEvictionQueue(Cache c) {
    ((InternalCache) c).getConsistentInfo();
  }

  private static String uniqueCounterSuffix() {
    return Long.toString(UNIQUE_NAME_COUNTER.incrementAndGet(), 36);
  }

  private static String deriveNameFromTestMethod(Object testInstance) {
    Exception ex = new Exception();
    String methodName = "default";
    for (StackTraceElement e : ex.getStackTrace()) {
      Method m = null;
      try {
        Class c = Class.forName(e.getClassName());
        m = c.getMethod(e.getMethodName());
      } catch (Exception | NoClassDefFoundError ignore) {
      }
      if (m == null) {
        continue;
      }
      Annotation a = m.getAnnotation(Test.class);
      if (a != null) {
        methodName = "CACHE-" + testInstance.getClass().getSimpleName() + "." + m.getName();
      }
    }
    return methodName;
  }

  public String generateUniqueCacheName(Object obj) {
    return deriveNameFromTestMethod(obj) + "-" + uniqueCounterSuffix();
  }

  public int countEntriesViaIteration() {
    provideCache();
    int cnt = 0;
    for (CacheEntry e : ((Cache<?, ?>) cache).entries()) {
      cnt++;
    }
    return cnt;
  }

  public long getEffectiveSafetyGapMillis() {
    return TimingUnitTest.SHARP_EXPIRY_GAP_MILLIS;
  }

  public TimeReference getClock() {
    return clock;
  }

  /**
   * Return milliseconds since epoch based on the used clock implementation
   */
  public long ticks() { return getClock().ticks(); }

  public Instant now() { return getClock().ticksToInstant(ticks()); }

  public TimeStepper stepper() { return stepper; }

  public void await(Condition condition) {
    stepper.await(condition);
  }

  public void await(String description, Condition condition) {
    stepper.await(description, condition);
  }

  public void await(String description, long timeoutMillis, Condition condition) {
    stepper.await(description, timeoutMillis, condition);
  }

  public TimeBox within(long millis) {
    return new TimeBox(() -> clock.ticks(), millis);
  }

  public void await(long timeoutMillis, Condition condition) {
    stepper.await(timeoutMillis, condition);
  }

  /**
   * Wait at least for the specified amount of time unless the thread
   * gets interrupted.
   *
   * @throws RuntimeException if interrupted
   */
  public void sleep(long millis) {
    try {
      getClock().sleep(millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("interrupted", ex);
    }
  }

  public static class CountingLoader implements CacheLoader<Integer, Integer> {
    AtomicInteger counter = new AtomicInteger();

    public long getCount() {
      return counter.get();
    }

    @Override
    public Integer load(Integer key) throws Exception {
      return counter.getAndIncrement();
    }
  }

  public static class PatternLoader implements CacheLoader<Integer, Integer> {
    AtomicInteger counter = new AtomicInteger();
    int[] ints;

    public PatternLoader(int... values) {
      ints = values;
    }

    @Override
    public Integer load(Integer key) throws Exception {
      return ints[counter.getAndIncrement() % ints.length];
    }
  }

  public static class IdentCountingLoader implements CacheLoader<Integer, Integer> {
    AtomicInteger counter = new AtomicInteger();

    public long getCount() {
      return counter.get();
    }

    @Override
    public Integer load(Integer key) throws Exception {
      counter.getAndIncrement();
      return key;
    }
  }

  public static class IdentIntSource implements CacheLoader<Integer, Integer> {

    @Override
    public Integer load(Integer o) {
      return o;
    }
  }

  public void checkIntegrity(Cache c) {
    ((InternalCache) c).checkIntegrity();
  }

  /**
   * Wrap shared executor to make sure that at least {@value MINIMAL_LOADER_THREADS} are available
   * for each test
   */
  static class ExecutorWrapper implements Executor {

    volatile Executor fallBackExecutor = null;

    Executor getFallBack() {
      if (fallBackExecutor == null) {
        fallBackExecutor =
          new ThreadPoolExecutor(0, MINIMAL_LOADER_THREADS,
            21, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            ThreadFactoryProvider.DEFAULT.newThreadFactory(
              Thread.currentThread().getName() + "-loader-pool"),
            (r, executor) -> {
              throw new Error("more threads are needed then expected");
            });
      }
      return fallBackExecutor;
    }

    @Override
    public void execute(Runnable r) {
      try {
        SHARED_EXECUTOR.execute(r);
      } catch (RejectedExecutionException e) {
        getFallBack().execute(r);
      }
    }

    @Override
    public String toString() {
      return "ExecutorWrapper{" +
        "fallBackExecutor=" + fallBackExecutor +
        ", shared=" + SHARED_EXECUTOR +
        '}';
    }
  }

  public boolean isHeapCache() {
    provideCache();
    return cache.requestInterface(HeapCache.class) != null;
  }

  public boolean isWiredCache() {
    provideCache();
    try {
      cache.requestInterface(WiredCache.class);
      return true;
    } catch (UnsupportedOperationException ex) {
      return false;
    }
  }

  public SupervisedExecutor executor() {
    return new SupervisedExecutor(loaderExecutor);
  }

  private int pendingExecution = 0;

  /**
   * Execute concurrently.
   *
   * @see #join()
   */
  public synchronized void execute(Runnable action) {
    pendingExecution++;
    loaderExecutor.execute(() -> {
      try {
        action.run();
      } finally {
        synchronized (TestingBase.this) {
          pendingExecution--;
          if (pendingExecution == 0) {
            TestingBase.this.notifyAll();
          }
        }
      }
    });
  }

  /**
   * Wait for all concurrent jobs started with {@link #execute(Runnable)}
   */
  public void join() {
    synchronized (this) {
      while (pendingExecution > 0) {
        try {
          this.wait();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

}
