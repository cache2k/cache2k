package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.impl.threading.Futures;
import org.cache2k.impl.threading.GlobalPooledExecutor;
import org.cache2k.impl.util.Cache2kVersion;
import org.cache2k.impl.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;

/**
 * @author Jens Wilke; created: 2013-07-01
 */
public class CacheManagerImpl extends CacheManager {
  
  static List<CacheLifeCycleListener> cacheLifeCycleListeners = new ArrayList<CacheLifeCycleListener>();
  static List<CacheManagerLifeCycleListener> cacheManagerLifeCycleListeners = new ArrayList<CacheManagerLifeCycleListener>();

  static {
    ClassLoader cl = CacheManagerImpl.class.getClassLoader();
    for (CacheLifeCycleListener l : ServiceLoader.load(CacheLifeCycleListener.class, cl)) {
      cacheLifeCycleListeners.add(l);
    }
    for (CacheManagerLifeCycleListener l : ServiceLoader.load(CacheManagerLifeCycleListener.class, cl)) {
      cacheManagerLifeCycleListeners.add(l);
    }
  }

  private Map<String, StackTrace> name2CreationStackTrace = null;
  private Log log;
  private String name;
  private Map<String, BaseCache> cacheNames = new HashMap<String, BaseCache>();
  private Set<Cache> caches = new HashSet<Cache>();
  private int disambiguationCounter = 1;
  private GlobalPooledExecutor threadPool;
  private AtomicInteger evictionThreadCount = new AtomicInteger();
  private ExecutorService evictionExecutor;
  private String version;
  private String buildNumber;

  public CacheManagerImpl() {
    this(getDefaultName());
  }

  public CacheManagerImpl(String _name) {
    name = _name;
    log = Log.getLog(CacheManager.class.getName() + '.' + name);
    buildNumber = Cache2kVersion.getBuildNumber();
    version = Cache2kVersion.getVersion();
    StringBuilder sb = new StringBuilder();
    sb.append("org.cache2k manager starting. ");
    sb.append("name="); sb.append(name);
    sb.append(", version="); sb.append(version);
    sb.append(", build="); sb.append(buildNumber);
    boolean _traceCacheCreation = log.isDebugEnabled();
    log.info(sb.toString());
    for (CacheManagerLifeCycleListener lc : cacheManagerLifeCycleListeners) {
      lc.managerCreated(this);
    }
    if (_traceCacheCreation) {
      name2CreationStackTrace = new HashMap<String, StackTrace>();
    }
  }

  private void sendCreatedEvent(Cache c) {
    for (CacheLifeCycleListener e : cacheLifeCycleListeners) {
      e.cacheCreated(this, c);
    }
  }

  private void sendDestroyedEvent(Cache c) {
    for (CacheLifeCycleListener e : cacheLifeCycleListeners) {
      e.cacheDestroyed(this, c);
    }
  }

  /**
   * Don't accept a cache name with too weird characters. Rather then escaping the
   * name, so we can use it for JMX, it is better to just reject it.
   */
  private void checkName(String s) {
    for (char c : s.toCharArray()) {
      if (c == '.' ||
          c == '-' ||
          c == '~') {
        continue;
      }
      if (!Character.isJavaIdentifierPart(c)) {
        throw new CacheUsageExcpetion(
          "Cache name contains illegal chars: '" + c + "', name=\"" + s + "\"");
      }
    }
  }

  static class StackTrace extends Exception { }

  /* called by builder */
  public synchronized void newCache(Cache c) {
    BaseCache bc = (BaseCache) c;
    String _requestedName = c.getName();
    String _name = _requestedName;
    while (cacheNames.containsKey(_name)) {
      _name = _requestedName + "~" + Integer.toString(disambiguationCounter++, 36);
    }
    if (!_requestedName.equals(_name)) {
      log.warn("duplicate name, disambiguating: " + _requestedName + " -> " + _name, new StackTrace());
      if (name2CreationStackTrace != null) {
        log.warn("initial creation of " + _requestedName, name2CreationStackTrace.get(_requestedName));
      }
      bc.setName(_name);
    }
    checkName(_name);

    if (name2CreationStackTrace != null) {
      name2CreationStackTrace.put(_name, new StackTrace());
    }
    caches.add(c);
    sendCreatedEvent(c);
    bc.setCacheManager(this);
    cacheNames.put(c.getName(), bc);
  }

  /* called by cache or CM */
  public synchronized void cacheDestroyed(Cache c) {
    cacheNames.remove(c.getName());
    if (name2CreationStackTrace != null) {
      name2CreationStackTrace.remove(c.getName());
    }
    caches.remove(c);
    sendDestroyedEvent(c);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public synchronized Iterator<Cache> iterator() {
    checkClosed();
    return new HashSet<Cache>(caches).iterator();
  }

  @Override
  public Cache getCache(String name) {
    return cacheNames.get(name);
  }

  @Override
  public synchronized void clear() {
    checkClosed();
    for (Cache c : caches) {
      c.clear();
    }
  }

  @Override
  public synchronized void destroy() {
    close();
  }

  /**
   * The shutdown takes place in two phases. First all caches are notified to
   * cancel their scheduled timer jobs, after that the shutdown is done. Cancelling
   * the timer jobs first is needed, because there may be cache stacking and
   * a timer job of one cache may call an already closed cache.
   *
   * <p/>Rationale exception handling: Exceptions on shutdown just could silently
   * ignored, because a shutdown is done any way. Exceptions could be happened
   * long before in a parallel task, shutdown is the last point where this could
   * be propagated to the application. Silently ignoring is bad anyway, because
   * this will cause that serious problems keep undetected. The exception and
   * error handling within the cache tries everything that exceptions will be
   * routed through as early and directly as possible.
   */
  @Override
  public synchronized void close() {
    List<Throwable> _suppressedExceptions = new ArrayList<Throwable>();
    if (caches != null) {
      Futures.WaitForAllFuture<Void> _wait = new Futures.WaitForAllFuture<Void>();
      for (Cache c : caches) {
        if (c instanceof BaseCache) {
          try {
            Future<Void> f = ((BaseCache) c).cancelTimerJobs();
            _wait.add(f);
          } catch (Throwable t) {
            _suppressedExceptions.add(t);
          }
        }
      }
      try {
        _wait.get(3000, TimeUnit.MILLISECONDS);
      } catch (Exception ex) {
        _suppressedExceptions.add(ex);
      }
      if (!_wait.isDone()) {
        for (Cache c : caches) {
          if (c instanceof BaseCache) {
            BaseCache bc = (BaseCache) c;
            try {
              Future<Void> f = bc.cancelTimerJobs();
              if (!f.isDone()) {
                bc.getLog().info("fetches ongoing, terminating anyway...");
              }
            } catch (Throwable t) {
              _suppressedExceptions.add(t);
            }
          }
        }
      }
      Set<Cache> _caches = new HashSet<Cache>();
      _caches.addAll(caches);
      for (Cache c : _caches) {
        try {
          c.destroy();
        } catch (Throwable t) {
          _suppressedExceptions.add(t);
        }
      }
      try {
        if (threadPool != null) {
            threadPool.close();
        }
        for (CacheManagerLifeCycleListener lc : cacheManagerLifeCycleListeners) {
          lc.managerDestroyed(this);
        }
        caches = null;
      } catch (Throwable t) {
        _suppressedExceptions.add(t);
      }
    }
    eventuallyThrowException(_suppressedExceptions);
  }

  /**
   * Throw exception if the list contains exceptions. The the thrown exception
   * all exceptions get added as suppressed exceptions. The main cause of the
   * exception will be the first detected error or the first detected
   * exception.
   *
   * @throws org.cache2k.impl.CacheInternalError if list contains an error
   * @throws org.cache2k.CacheException if list does not contain an error
   */
  private void eventuallyThrowException(List<Throwable> _suppressedExceptions) {
    if (_suppressedExceptions.size() > 0) {
      Throwable _error = null;
      for (Throwable t : _suppressedExceptions) {
        if (t instanceof Error) { _error = t; }
        if (t instanceof ExecutionException &&
          ((ExecutionException) t).getCause() instanceof Error) {
          _error = t;
        }
      }
      Throwable _throwNow;
      String _text = "shutdown";
      if (_suppressedExceptions.size() > 1) {
        _text = " (" + _suppressedExceptions.size() + " exceptions)";
      }
      if (_error != null) {
        _throwNow = new CacheInternalError(_text, _error);
      } else {
        _throwNow = new CacheException(_text, _suppressedExceptions.get(0));
        _suppressedExceptions.remove(0);
      }
      for (Throwable t : _suppressedExceptions) {
        if (t != _error) {
          _throwNow.addSuppressed(t);
        }
      }
      if (_error != null) {
        throw (Error) _throwNow;
      } else {
        throw (RuntimeException) _throwNow;
      }
    }
  }

  @Override
  public boolean isDestroyed() {
    return caches == null;
  }

  private String getThreadNamePrefix() {
    if (!DEFAULT_MANAGER_NAME.equals(name)) {
      return "cache2k:" + name + ":";
    }
    return "cache2k-";
  }

  /**
   * Lazy creation of thread pool, usable for all caches managed by the cache
   * manager.
   */
  public synchronized GlobalPooledExecutor getThreadPool() {
    if (threadPool == null) {
      threadPool = new GlobalPooledExecutor(getThreadNamePrefix() + "pool-");
    }
    return threadPool;
  }

  public ExecutorService createEvictionExecutor() {
    ThreadFactory _threadFactory = new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        String _name =
            getThreadNamePrefix() + "eviction-" + evictionThreadCount.incrementAndGet();
        Thread t = new Thread(new ExceptionWrapper(r), _name);
        t.setDaemon(true);
        return t;
      }
    };
    return
      new ThreadPoolExecutor(
        0, Integer.MAX_VALUE, 17, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(Runtime.getRuntime().availableProcessors() * 2), _threadFactory);
  }

  class ExceptionWrapper implements Runnable {

    Runnable runnable;

    ExceptionWrapper(Runnable runnable) {
      this.runnable = runnable;
    }

    @Override
    public void run() {
      try {
        runnable.run();
      } catch (CacheClosedException ignore) {
       } catch (Throwable t) {
        log.warn("Exception in thread \"" + Thread.currentThread().getName() + "\"", t);
      }
    }

  }

  public ExecutorService getEvictionExecutor() {
    if (evictionExecutor == null) {
      evictionExecutor = createEvictionExecutor();
    }
    return evictionExecutor;
  }

  /**
   * Only return thread pool if created before. For JMX bean access.
   */
  public GlobalPooledExecutor getThreadPoolEventually() {
    return threadPool;
  }

  private void checkClosed() {
    if (caches == null) {
      throw new IllegalStateException("CacheManager already closed");
    }
  }

  public String getVersion() {
    return version;
  }

  public String getBuildNumber() {
    return buildNumber;
  }

}
