package org.cache2k.testsuite.api;

/*-
 * #%L
 * cache2k testsuite on public API
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
import org.cache2k.annotation.NonNull;
import org.cache2k.annotation.Nullable;
import static org.junit.jupiter.api.Assertions.*;

import org.cache2k.processor.MutableCacheEntry;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("StringConcatenationInLoop")
public class NullnessTest {

  /**
   * Specify value as nullable by type annotation. NullAway does not
   * recognize this. What about other tools?
   */
  @SuppressWarnings("NullAway")
  @Test
  public void nullableTypeAnnotation() {
    Cache<Integer, @Nullable String> cache =
      new Cache2kBuilder<Integer, @Nullable String>() { }
      .permitNullValues(true)
      .build();
    cache.put(123, null);
    cache.put(125, "abc");
    cache.close();
  }

  @Test
  public void nonNullTypeAnnotation() {
    Cache<Integer, @NonNull String> cache =
      new Cache2kBuilder<Integer, @NonNull String>() { }
        .build();
    cache.put(125, "abc");
  }

  @Test
  public void iteration_GetAll() {
    Cache<Integer, String> cache =
      new Cache2kBuilder<Integer, String>() { }
        .build();
    cache.put(125, "abc");
    cache.put(345, "Paloma");
    cache.put(543, "Fraser Island");
    String txt = "";
    for (CacheEntry<Integer, String> e : cache.entries()) {
      txt += e.getValue().length();
    }
    for (String s : cache.asMap().values()) {
      txt += s.length();
    }
    for (String s : cache.getAll(asList(1, 2, 3)).values()) {
      txt += s.length();
    }
    assertEquals("61336133", txt);
    String s = cache.invoke(345, MutableCacheEntry::getValue);
  }

  @SuppressWarnings("nullness")
  @Test
  public void invoke_get() {
    Cache<Integer, String> cache =
      new Cache2kBuilder<Integer, String>() { }
        .build();
    cache.invoke(345, MutableCacheEntry::getValue);
  }

  <T> @NonNull T nonNull(@Nullable T obj) {
    if (obj == null) {
      throw new NullPointerException();
    }
    return obj;
  }

  @Test
  public void increment() {
    Cache<Integer, Integer> cache =
      new Cache2kBuilder<Integer, Integer>() { }
      .build();
    cache.put(123, 1);
    cache.mutate(123, entry -> entry.setValue(nonNull(entry.getValue()) + 1));
  }

}
