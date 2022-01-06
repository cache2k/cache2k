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

/**
 * @author Jens Wilke
 */
public class TestContext<K, V> {

  public static final TestContext<Integer, Integer> DEFAULT =
    new TestContext<>(DataType.INT_KEYS, DataType.INT_VALUES);

  private DataType<K> keys;
  private DataType<V> values;

  public TestContext(DataType<K> keys, DataType<V> values) {
    this.keys = keys;
    this.values = values;
  }

  public DataType<K> getKeys() {
    return keys;
  }

  public DataType<V> getValues() {
    return values;
  }

}
