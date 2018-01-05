package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

/**
 * An internal error condition in the cache was detected that actually
 * never is supposed to happen. If you get this error or subclasses of it,
 * please file a bug report.
 *
 * @author Jens Wilke; created: 2014-06-03
 */
public class CacheInternalError extends Error {

  public CacheInternalError() {
  }

  public CacheInternalError(String message) {
    super(message);
  }

  public CacheInternalError(String message, Throwable cause) {
    super(message, cause);
  }

  public CacheInternalError(Throwable cause) {
    super(cause);
  }

  public CacheInternalError(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

}
