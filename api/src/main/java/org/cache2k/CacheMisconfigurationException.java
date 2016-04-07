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

/**
 * A misconfiguration upon cache initialization is detected.
 * The cache is unable to operate.
 *
 * @author Jens Wilke; created: 2014-08-17
 */
public class CacheMisconfigurationException extends CacheException {

  public CacheMisconfigurationException() {
    super();
  }

  public CacheMisconfigurationException(String message) {
    super(message);
  }

  public CacheMisconfigurationException(String message, Throwable cause) {
    super(message, cause);
  }

  public CacheMisconfigurationException(Throwable cause) {
    super(cause);
  }

  public CacheMisconfigurationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

}
