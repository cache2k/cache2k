package org.cache2k.testsuite.support;

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

import org.cache2k.config.CacheType;
import org.cache2k.config.CacheTypeCapture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Jens Wilke
 */
public class DataType<T> {

  public static final DataType<Integer> INT_KEYS = new DataType<Integer>(
    new Supplier<Integer>() {
      private int count = 0;
      @Override
      public Integer get() {
        return count++;
      }
    }, new CacheTypeCapture.OfClass<>(Integer.class));

  public static final DataType<Integer> INT_VALUES = new DataType<Integer>(
    new Supplier<Integer>() {
      private int count = 1000;
      @Override
      public Integer get() {
        return count++;
      }
    }, new CacheTypeCapture.OfClass<>(Integer.class));

  public static final DataType<Object> OBJ_KEYS = new DataType<Object>(
    new Supplier<Object>() {
      private int count = 0;
      @Override
      public Object get() {
        return count++;
      }
    }, new CacheTypeCapture.OfClass<>(Object.class));

  public static final DataType<Object> OBJ_VALUES = new DataType<Object>(
    new Supplier<Object>() {
      private int count = 1000;
      @Override
      public Object get() {
        return count++;
      }
    }, new CacheTypeCapture.OfClass<>(Object.class));

  private volatile Map<T, Integer> valueToIndex = Collections.emptyMap();
  private volatile T[] generatedValues = (T[]) new Object[0];
  private final Supplier<T> anotherValue;
  private final CacheType<T> cacheType;
  private final T value0;
  private final T value1;
  private final T value2;

  public DataType(Supplier<T> anotherValue, CacheType<T> cacheType) {
    this.anotherValue = anotherValue;
    this.cacheType = cacheType;
    value2 = get(2);
    value0 = get(0);
    value1 = get(1);
  }

  public T get(int index) {
    T[] vals = generatedValues;
    if (vals.length > index) {
      return vals[index];
    }
    synchronized (this) {
      vals = generatedValues;
      if (vals.length > index) {
        return vals[index];
      }
      T[] moreVals = (T[]) new Object[index + 1];
      System.arraycopy(vals, 0, moreVals, 0, vals.length);
      for (int i = vals.length; i <= index; i++) {
        moreVals[i] = anotherValue.get();
      }
      generatedValues = moreVals;
      return moreVals[index];
    }
  }

  public int toIndex(T value) {
    Integer index = valueToIndex.get(value);
    if (index == null) {
      synchronized (this) {
        Map<T, Integer> newMap = new HashMap<>();
        for (int i = 0; i < generatedValues.length; i++) {
          newMap.put(generatedValues[i], i);
        }
        valueToIndex = newMap;
        index = newMap.get(value);
        if (index == null) {
          throw new AssertionError("unknown value: " + value);
        }
      }
    }
    return index;
  }

  public T getValue0() {
    return value0;
  }

  public T getValue1() {
    return value1;
  }

  public T getValue2() {
    return value2;
  }

  public CacheType<T> getCacheType() {
    return cacheType;
  }

}
