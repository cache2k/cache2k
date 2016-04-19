package org.cache2k.core.operation;

/*
 * #%L
 * cache2k core package
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

/**
 * A entry on the heap cache, used for reading.
 * Only the relevant properties are defined to implement the cache
 * semantics on it.
 *
 * <p>This interface is used to make sure the operation semantics
 * pass on the valueOrException property as result, which is needed
 * to propagate the exception correctly.
 *
 * @author Jens Wilke
 */
public interface ExaminationEntry<K, V> {

  /** Associated key */
  K getKey();

  /** Associated value or the {@link org.cache2k.core.ExceptionWrapper} */
  V getValueOrException();

  long getLastModification();

}
