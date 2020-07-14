package org.cache2k.integration;

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
 */
public interface ExceptionPropagator<K> {

  /**
   * Generate runtime exception to throw. The original exception is passed in as information.
   * Every returned exception should be filled with a stack trace based on the current
   * method call. This is done by the exception constructor automatically.
   *
   * @param exceptionInformation information when the original exception occurred.
   */
  RuntimeException propagateException(K key, ExceptionInformation exceptionInformation);

}
