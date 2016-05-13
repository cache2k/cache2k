package org.cache2k.test.util;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
import org.cache2k.configuration.CacheTypeCapture;
import org.cache2k.configuration.CacheType;
import org.cache2k.core.InternalCache;
import org.cache2k.core.InternalCacheInfo;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jens Wilke
 */
public class CacheRule<K,V> implements TestRule {

  /**
   * Record classes that have shared caches, just to throw a better exception.
   */
  private static Map<String, String> sharedCache = new ConcurrentHashMap<String, String>();

  /** It is a class rule and we want to share the cache between the methods */
  boolean shared;
  Cache<K,V> cache;
  Description description;
  CacheType<K> keyType;
  CacheType<V> valueType;
  Cache2kBuilder<K,V> builder;

  @SuppressWarnings("unchecked")
  protected CacheRule() {
    Type[] _types =
      ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments();
    keyType =
      (CacheType<K>) CacheTypeCapture.of(_types[0]).getBeanRepresentation();
    valueType =
      (CacheType<V>) CacheTypeCapture.of(_types[1]).getBeanRepresentation();
    builder = getInitialBuilder();
  }

  private Cache2kBuilder<K, V> getInitialBuilder() {
    return Cache2kBuilder.forUnknownTypes()
      .keyType(keyType)
      .valueType(valueType)
      .entryCapacity(10000)
      .eternal(true);
  }

  public CacheRule<K,V> config(Specialization<K,V> rb) {
    checkAlready();
    rb.extend(builder);
    return this;
  }

  private void checkAlready() {
    if (cache != null) {
      throw new IllegalStateException("cache already build");
    }
  }

  /**
   * Create a cache or return an existing cache.
   */
  public Cache<K,V> cache() {
    if (cache == null) {
      provideCache();
    }
    return cache;
  }

  /**
   * Create a cache with additional special configuration.
   */
  public Cache<K,V> cache(Specialization<K,V> rb) {
    config(rb);
    provideCache();
    return cache;
  }

  public void run(Context<K,V> rb) {
    config(rb);
    provideCache();
    rb.cache = cache;
    rb.run();
  }

  /**
   * Return cache, expects it to be build or set already.
   */
  public Cache<K,V> getCache() {
    if (cache == null) {
      throw new NullPointerException("cache not yet built");
    }
    return cache;
  }

  /**
   * Set a pre built cache to be managed by this rule.
   */
  public void setCache(Cache<K,V> c) {
    checkAlready();
    cache = c;
  }

  public InternalCacheInfo info() {
    return cache.requestInterface(InternalCache.class).getLatestInfo();
  }

  @Override
  public Statement apply(final Statement st, final Description d) {
    if (d.isSuite()) {
      shared = true;
      description = d;
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          try {
            st.evaluate();
          } finally {
            cleanupClass();
          }
        }
      };
    }
    if (d.isTest()) {
      description = d;
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          try {
            st.evaluate();
          } finally {
            cleanupMethod();
          }
        }
      };
    }
    throw new UnsupportedOperationException("hey?");
  }

  void cleanupMethod() {
    if (shared) {
      try {
        cache.clear();
      } catch (Throwable ignore) { }
    } else {
      try {
        cache.clear();
        cache.close();
      } catch (Throwable ignore) { }
    }
  }

  void cleanupClass() {
    if (cache != null) {
      try {
        cache.clear();
        cache.close();
      } catch (Throwable ignore) { }
    }
  }

  void provideCache() {
    if (shared) {
      if (cache == null) {
        cache = buildCache();
      }
    } else {
      cache = buildCache();
    }
  }

  Cache<K, V> buildCache() {
    String _name = description.getTestClass().getName();
    if (shared) {
      builder.name(description.getTestClass());
      sharedCache.put(_name, _name);
    } else {
      if (sharedCache.containsKey(_name)) {
        throw new IllegalArgumentException("Shared cache usage: Method rule must be identical instance.");
      }
      builder.name(description.getTestClass(), description.getMethodName());
    }
    return builder.build();
  }

  public interface Specialization<K,V> {
    void extend(Cache2kBuilder<K,V> b);
  }

  public static abstract class Context<K,V> implements Specialization<K,V>, Runnable {

    public Cache<K,V> cache;

    /** Used to run the tests with the context object */
    public void run() { }

  }

}
