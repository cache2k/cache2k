package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
  private Map<K, V> map = new HashMap<>();

  public void put(K key, V value) {
    map.put(key, value);
    if (value instanceof ExceptionWrapper) {
      anyException = ((ExceptionWrapper<?, ?>) value).getException();
      exceptionCount++;
    }
  }

  public Map<K, V> getMap() {
    return new MapValueConverterProxy<K, V, V>(map) {
      @Override
      protected V convert(V v) {
        return HeapCache.returnValue(v);
      }
    };
  }

  public void putAll(Collection<EntryAction<K, V, V>> actions) {
    for (EntryAction<K, V, V> action : actions) {
      if (action.isResultAvailable()) {
        put(action.getKey(), action.getResult());
      }
    }
  }

}
