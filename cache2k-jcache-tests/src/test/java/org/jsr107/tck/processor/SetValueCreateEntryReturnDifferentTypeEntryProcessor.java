/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.processor;

import org.junit.Assert;

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import java.io.Serializable;

/**
 * Specialized Entry processor that can return a different type and value than the entry value.
 *
 * @param <K> key type
 * @param <V> value type
 * @param <T> process return type
 */
public class SetValueCreateEntryReturnDifferentTypeEntryProcessor<K, V, T> implements EntryProcessor<K, V, T>, Serializable {

  /**
   * The value to set.
   */
  private V value;

  /**
   * The result to return.
   */
  private T result;


  /**
   * Constructs a {@link SetValueCreateEntryReturnDifferentTypeEntryProcessor}.
   *
   * @param result   process result
   * @param newValue new entry value
   */
  public SetValueCreateEntryReturnDifferentTypeEntryProcessor(T result, V newValue) {
    this.value = newValue;
    this.result = result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T process(MutableEntry<K, V> entry, Object... arguments) {
    Assert.assertFalse(entry.exists());
    entry.setValue(value);
    Assert.assertTrue(entry.exists());

    return result;
  }
}
