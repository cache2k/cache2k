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

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import java.io.Serializable;

/**
 * An {@link EntryProcessor} to set the value of an entry.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class SetEntryProcessor<K, V> implements EntryProcessor<K, V, V>, Serializable {

  /**
   * The value to set.
   */
  private V value;

  /**
   * Constructs a {@link SetEntryProcessor}.
   *
   * @param value entry value
   */
  public SetEntryProcessor(V value) {
    this.value = value;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public V process(MutableEntry<K, V> entry, Object... arguments) {
    entry.setValue(value);

    return entry.getValue();
  }

  /**
   * Obtains the value to set.
   *
   * @return the value to set
   */
  public V getValue() {
    return value;
  }
}
