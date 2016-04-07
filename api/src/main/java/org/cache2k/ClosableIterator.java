package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

import java.io.Closeable;
import java.util.Iterator;

/**
 * After the usage of the iterator close should be called to free resources.
 * The cache also alters the hash table operations for the life of the iterator
 * and switches back to normal after the iteration is finished.
 *
 * @author Jens Wilke; created: 2014-06-07
 */
public interface ClosableIterator<E> extends Iterator<E>, Closeable {

  /**
   * Immediately free resources held by the iterator. Overrides {@link java.io.Closeable#close()}
   * since no checked exceptions are thrown by the method.
   */
  @Override
  public void close();

}
