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

import org.cache2k.CustomizationException;

/**
 * Exception to wrap a loader exception. When an exception is thrown by the loader
 * and rethrown to the application it is wrapped in this exception.
 *
 * <p>It is possible to register a custom behavior how exceptions are rethrown (propagated)
 * with the {@link ExceptionPropagator}.
 *
 * <p>A single loader exception may be thrown multiple times to the application, if
 * the exception is cached. On the other hand, it is possible to suppress a loader
 * exception if the cache still contains data. This is controlled by the{@link ResiliencePolicy}.
 *
 * <p>In case one cached exception is thrown many times with in a timespan, it contains
 * the string {@code expiry=<timestamp>}. This is the behavior of the standard
 * {@link ExceptionPropagator}
 *
 * @author Jens Wilke
 * @since 2
 */
public class CacheLoaderException extends CustomizationException {

  public CacheLoaderException(String message) {
    super(message);
  }

  public CacheLoaderException(String message, Throwable ex) {
    super(message, ex);
  }

  public CacheLoaderException(Throwable ex) {
    super(ex);
  }

}
