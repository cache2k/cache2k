package org.cache2k.test.util;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CacheManager;
import org.cache2k.core.CanCheckIntegrity;
import org.cache2k.core.HeapCache;
import org.cache2k.core.InternalCache;
import org.cache2k.core.InternalCacheInfo;
import org.cache2k.core.util.ClockDefaultImpl;
import org.cache2k.core.util.InternalClock;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.core.util.SimulatedClock;
import org.cache2k.integration.CacheLoader;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.test.core.CacheLoaderTest;
import org.cache2k.test.core.Statistics;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Base class for most of the cache tests. Provides a separate cache for
 * each test within the default cache manager. Provides methods for testing the timing
 * like {@link #millis()}, {@link #within(long)} and {@link #sleep(long)}, that may be
 * backed via a simulated clock implementation {@link #enableFastClock()}.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("unchecked")
public class TestingBase {

  public final int DEFAULT_MAX_SIZE = 200;

  public static final int MINIMAL_LOADER_THREADS = 4;

  private static final ThreadLocal<SimulatedClock> THREAD_CLOCK = new ThreadLocal<SimulatedClock>() {
    @Override
    protected SimulatedClock initialValue() {
      return new SimulatedClock(1000000, false);
    }
  };

  private static final AtomicLong uniqueNameCounter = new AtomicLong();
  public static final Executor SHARED_EXECUTOR =
    new ThreadPoolExecutor(4, 4 * Runtime.getRuntime().availableProcessors(),
      21, TimeUnit.SECONDS,
      new SynchronousQueue<Runnable>(),
      HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory("test-loader-pool"),
      new ThreadPoolExecutor.AbortPolicy());

  private Executor loaderExecutor = new ExecutorWrapper();

  public Executor getLoaderExecutor() {
    return loaderExecutor;
  }

  private final static TimeStepper defaultStepper = new TimeStepper(ClockDefaultImpl.INSTANCE);
  private TimeStepper stepper = defaultStepper;
  private InternalClock clock;
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
             public Statement apply(final Statement base, final Description description) {
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
                         System.err.println(loaderExecutor);
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
  private static Class<?> defaultImplementation = null;

  static {
    if (System.getProperty("logOnInfo") == null) {
      Logger log = LogManager.getLogManager().getLogger("");
      for (Handler h : log.getHandlers()) {
        h.setLevel(Level.WARNING);
      }
    }
  }

  protected Class<?> getCacheImplementation() { return defaultImplementation; }

  public void setClock(InternalClock c) {
    if (clock != null) {
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
  protected Cache2kBuilder<Integer,Integer> builder() {
    return builder(Integer.class, Integer.class);
  }

  protected <K,T> Cache2kBuilder<K,T> builder(Class<K> k, Class<T> t) {
    return builder(generateUniqueCacheName(this), k, t);
  }

  protected <K,T> Cache2kBuilder<K,T> builder(String _cacheName, Class<K> k, Class<T> t) {
    provideCache();
    if (cache != null) {
      checkIntegrity();
      cache.clear();
      cache.close();
      cache = null;
    }
    cacheName = _cacheName;
    Cache2kBuilder<K,T> b = Cache2kBuilder.of(k,t)
      .timeReference(getClock())
      .name(_cacheName)
      .entryCapacity(DEFAULT_MAX_SIZE)
      .loaderExecutor(loaderExecutor);
    applyAdditionalOptions(b);
    return b;
  }

  /**
   *
   * @param b
   */
  protected void applyAdditionalOptions(Cache2kBuilder b) { }

  protected void applyMaxElements(Cache2kBuilder b, long _maxElements) {
    b.entryCapacity(_maxElements);
  }

  protected <K, T> Cache<K, T> freshCache(
      Class<K> _keyClass, Class<T> _dataClass, CacheLoader g, long _maxElements, int _expiry) {
    Cache2kBuilder<K, T> b =
      builder(_keyClass, _dataClass).loader(g).refreshAhead(_expiry >= 0 && g != null);
    if (_expiry < 0) {
      b.eternal(true);
    } else {
      b.expireAfterWrite(_expiry, TimeUnit.SECONDS);
    }
    applyMaxElements(b, _maxElements);
    return cache = b.build();
  }

  protected Cache<Integer, Integer> freshCache(CacheLoader<Integer, Integer> g, long _maxElements, int _expirySeconds) {
    return freshCache(Integer.class, Integer.class, g, _maxElements, _expirySeconds);
  }

  protected Cache<Integer, Integer> freshCache(CacheLoader<Integer, Integer> g, long _maxElements) {
    return freshCache(g, _maxElements, 5 * 60);
  }

  public void cleanup() {
    try {
      boolean _debug = false;
      if (_debug) {
        System.err.println("tearDown: " + cache);
        if (cache instanceof InternalCache) {
          InternalCache bc = (InternalCache) cache;
          if (bc.getStorage() != null) {
            System.err.println("tearDown, storage: " + bc.getStorage());
          }
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
    assertEquals("fetchesInFlight == 0", 0, statLoadsInFlight(cache, inf));
    assertEquals("exception count = 0", 0, inf.getInternalExceptionCount());
    assertNotNull("cache was tested", cache);
    if (cache instanceof CanCheckIntegrity) {
      ((CanCheckIntegrity) cache).checkIntegrity();
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
        assertFalse("cache is not closed", cache.isClosed());
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
        CacheManager cm = CacheManager.getInstance();
        cache = cm.getCache(cacheName);
      }
      assertNotNull("cache is available", cache);
      assertFalse("cache is not closed", cache.isClosed());
    }
  }

  protected void checkIntegrity() {
    provideCache();
    if (cache instanceof CanCheckIntegrity) {
      ((CanCheckIntegrity) cache).checkIntegrity();
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
    return getInternalCache().getLatestInfo();
  }

  protected void debugEntry(Object key) {
    System.out.println(getInternalCache().getEntryState(key));
  }

  protected void drainEvictionQueue() {
    getInfo();
  }

  protected void drainEvictionQueue(Cache c) {
    ((InternalCache) c).getLatestInfo();
  }

  private static String uniqueCounterSuffix() {
    return Long.toString(uniqueNameCounter.incrementAndGet(), 36);
  }

  private static String deriveNameFromTestMethod(Object _testInstance) {
    Exception ex = new Exception();
    String _methodName = "default";
    for (StackTraceElement e : ex.getStackTrace()) {
      Method m = null;
      try {
        Class <?> c = Class.forName(e.getClassName());
        m = c.getMethod(e.getMethodName());
      } catch (Exception ignore) {
      } catch (NoClassDefFoundError ignore) {
      }
      if (m == null) {
        continue;
      }
      Annotation a = m.getAnnotation(Test.class);
      if (a != null) {
        _methodName = "CACHE-" + _testInstance.getClass().getSimpleName() + "." + m.getName();
      }
    }
    return _methodName;
  }

  public String generateUniqueCacheName(Object obj) {
    return deriveNameFromTestMethod(obj) + "-" + uniqueCounterSuffix();
  }

  public int countEntriesViaIteration() {
    provideCache();
    int cnt = 0;
    for (CacheEntry e : ((Cache<?,?>) cache).entries()) {
      cnt++;
    }
    return cnt;
  }

  public long getEffectiveSafetyGapMillis() {
    HeapCache.Tunable t = TunableFactory.get(HeapCache.Tunable.class);
    return t.sharpExpirySafetyGapMillis;
  }

  public InternalClock getClock() {
    if (clock == null) {
      setClock(ClockDefaultImpl.INSTANCE);
    }
    return clock;
  }

  /**
   * Return milliseconds since epoch based on the used clock implementation
   */
  public long millis() { return getClock().millis(); }

  public TimeStepper stepper() { return stepper; }

  public void await(Condition _condition) {
    stepper.await(_condition);
  }

  public void await(String _description, Condition _condition) {
    stepper.await(_description, _condition);
  }

  public void await(String _description, long _timeoutMillis, Condition _condition) {
    stepper.await(_description, _timeoutMillis, _condition);
  }

  public TimeBox within(long _millis) {
    return new TimeBox(clock, _millis);
  }

  public void await(long _timeoutMillis, Condition _condition) {
    stepper.await(_timeoutMillis, _condition);
  }

  /**
   * Wait at least for the specified amount of time unless the thread
   * gets interrupted.
   *
   * @throws RuntimeException if interrupted
   */
  public void sleep(long _millis) {
    try {
      getClock().sleep(_millis);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("interrupted", ex);
    }
  }

  public static class CountingLoader extends CacheLoader<Integer, Integer> {
    AtomicInteger counter = new AtomicInteger();

    public long getCount() {
      return counter.get();
    }

    @Override
    public Integer load(final Integer key) throws Exception {
      return counter.getAndIncrement();
    }
  }

  public static class PatternLoader extends CacheLoader<Integer, Integer> {
    AtomicInteger counter = new AtomicInteger();
    int[] ints;

    public PatternLoader(final int... _ints) {
      ints = _ints;
    }

    @Override
    public Integer load(final Integer key) throws Exception {
      return ints[counter.getAndIncrement() % ints.length];
    }
  }

  public static class IdentCountingLoader extends CacheLoader<Integer, Integer> {
    AtomicInteger counter = new AtomicInteger();

    public long getCount() {
      return counter.get();
    }

    @Override
    public Integer load(final Integer key) throws Exception {
      counter.getAndIncrement();
      return key;
    }
  }

  public static class IdentIntSource extends CacheLoader<Integer, Integer> {

    @Override
    public Integer load(Integer o) {
      return o;
    }
  }

  public void checkIntegrity(Cache c) {
    ((CanCheckIntegrity) c).checkIntegrity();
  }

  public Throwable syncLoad(LoaderStarter x) {
    CacheLoaderTest.CompletionWaiter w = new CacheLoaderTest.CompletionWaiter();
    x.startLoad(w);
    w.awaitCompletion();
    return w.getException();
  }

  public interface LoaderStarter {
    void startLoad(CacheOperationCompletionListener l);
  }

  public void reload(int... keys) {
    provideCache();
    reload(cache, keys);
  }

  public static void reload(Cache c, int... keys) {
    CacheLoaderTest.CompletionWaiter w = new CacheLoaderTest.CompletionWaiter();
    List<Integer> l = new ArrayList<Integer>();
    for (int i : keys) {
      l.add(i);
    }
    c.reloadAll(l, w);
    w.awaitCompletion();
  }

  /**
   * Wrap shared executor to make sure that at least {@value MINIMAL_LOADER_THREADS} are available
   * for each test
   */
  static class ExecutorWrapper implements Executor{

    volatile Executor fallBackExecutor;

    Executor getFallBack() {
      if (fallBackExecutor == null) {
        fallBackExecutor =
          new ThreadPoolExecutor(0, MINIMAL_LOADER_THREADS,
            21, TimeUnit.SECONDS,
            new SynchronousQueue<Runnable>(),
            HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory(Thread.currentThread().getName() + "-loader-pool"),
            new RejectedExecutionHandler() {
              @Override
              public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
                throw new Error("more threads are needed then expected");
              }
            });
      }
      return fallBackExecutor;
    }

    @Override
    public void execute(final Runnable r) {
      try {
        SHARED_EXECUTOR.execute(r);
      } catch (RejectedExecutionException e) {
        getFallBack().execute(r);
      }
    }

  }

  public static <T> Iterable<T> keys(T... keys) {
    return Arrays.asList(keys);
  }

}
