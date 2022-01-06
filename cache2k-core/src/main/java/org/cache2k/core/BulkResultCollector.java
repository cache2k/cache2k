package org.cache2k.core;

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

import org.cache2k.io.CacheLoaderException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects result values of a bulk operation.
 *
 * @author Jens Wilke
 */
public class BulkResultCollector<K, V> {

  private int exceptionCount;
  private Throwable anyException;
  private Map<K, Object> map = new HashMap<>();

  public void put(K key, Object value) {
    map.put(key, value);
    if (value instanceof ExceptionWrapper) {
      anyException = ((ExceptionWrapper<?, ?>) value).getException();
      exceptionCount++;
    }
  }

  /**
   * Return result map or throw exception if every map entry has an exception.
   */
  public Map<K, V> mapOrThrowIfAllFaulty() {
    if (exceptionCount > 0 && exceptionCount == map.size()) {
      throw getAnyLoaderException();
    }
    return new MapValueConverterProxy<K, V, Object>(map) {
      @Override
      protected V convert(Object v) {
        return HeapCache.returnValue(v);
      }
    };
  }

  /**
   * Gets a loader exception if any value represents an exception
   *
   * @return CacheLoaderException or null if no exception is present
   */
  public CacheLoaderException getAnyLoaderException() {
    if (exceptionCount == 0) {
      return null;
    }
    return createBulkLoaderException(exceptionCount, map.size(), anyException);
  }

  public void throwOnAnyLoaderException() {
    if (exceptionCount > 0) {
      throw getAnyLoaderException();
    }
  }

  public static CacheLoaderException createBulkLoaderException(
    int exceptionCount, int operationCount, Throwable example) {
    if (exceptionCount > 1) {
      String txt =
        exceptionCount + " out of " + operationCount + " requests" +
        ", one as cause";
      return new CacheLoaderException(txt, example);
    }
    return new CacheLoaderException(example);
  }

  public void putAll(Collection<EntryAction<K, V, V>> actions) {
    for (EntryAction<K, V, V> action : actions) {
      if (action.isResultAvailable()) {
        put(action.getKey(), action.getResult());
      }
    }
  }

}
