package org.cache2k.testsuite.support;

/*
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.io.CacheLoader;
import org.cache2k.annotation.NonNull;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Jens Wilke
 */
public class Loaders {

  public static class IdentLoader<@NonNull T> implements CacheLoader<T, T> {

    @Override
    public T load(T o) {
      return o;
    }

  }

  static final CacheLoader IDENT_LOADER = new IdentLoader();

  public static <K, V> CacheLoader<K, V> identLoader() { return IDENT_LOADER; }

  public static class IdentCountingLoader<T> implements CacheLoader<T, T> {
    AtomicInteger counter = new AtomicInteger();

    public long getCallCount() {
      return counter.get();
    }

    @Override
    public T load(T key) throws Exception {
      counter.getAndIncrement();
      return key;
    }
  }

  public static class CountingLoader implements CacheLoader<Integer, Integer> {
    AtomicInteger counter = new AtomicInteger();

    public long getCallCount() {
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

}
