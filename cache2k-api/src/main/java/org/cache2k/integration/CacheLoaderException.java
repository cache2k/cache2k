package org.cache2k.integration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
 * exception if the cache still contains data. This is controlled by the
 * {@link ResiliencePolicy} or various configuration parameters (see the references).
 * In case a cached exception is thrown, it contains the string {@code expiry=<timestamp>}
 *
 * <p>As a general rule, throwing this exception is delayed as long as possible, when
 * an associated value for a specific key is requested that has caused the exception.
 * For example {@link org.cache2k.Cache#getEntry} will not throw this exception but
 * {@link org.cache2k.CacheEntry#getValue} will. For bulk methods for example
 * {@link org.cache2k.Cache#getAll} the exception will be thrown when the respective
 * value is accessed from the returned map. In case of a general error, for example the loader
 * produces an exception for every key, exceptions may be thrown as soon as possible
 * (fail fast principle).
 *
 * @author Jens Wilke
 */
public class CacheLoaderException extends CustomizationException {

  public CacheLoaderException(String _message, Throwable ex) {
    super(_message, ex);
  }

  public CacheLoaderException(Throwable ex) {
    super(ex);
  }

}
