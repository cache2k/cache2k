package org.cache2k.io;

/*
 * #%L
 * cache2k API
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

import org.cache2k.Customization;

/**
 * In read through mode exceptions are cached. Every time an entry is requested from the
 * cache a new exception is generated that wraps the original loader exception in a
 * {@link CacheLoaderException}. This behavior can be modified by registering a custom
 * exception propagator.
 *
 * <p>Exceptions should not be thrown directly but wrapped. Using this customization it
 * is possible to change the exception type or the message according to the information
 * available.
 *
 * @author Jens Wilke
 * @since 2
 */
public interface ExceptionPropagator<K> extends Customization {

  /**
   * Called when an entry with exception is accessed.
   * Potentially wraps and rethrows the original exception.
   *
   * <p>The default implementation wraps the exception into a {@link CacheLoaderException}
   * and contains some id or timestamp of the original exception to show that we might
   * through multiple exceptions on each entry access for a single loader exception.
   *
   * <p>API rationale: We create an exception instead of doing a {@code throw} in the
   * exception propagator, to keep the control flow obvious in the calling method.
   *
   * @param loadExceptionInfo information of original exception and
   *                             when the original exception occurred.
   */
  RuntimeException propagateEntryLoadException(LoadExceptionInfo<K> loadExceptionInfo);

}
