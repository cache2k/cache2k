package org.cache2k;

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

import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.io.CacheLoader;

/**
 * Wraps an exception thrown from a cache customization such as the
 * {@link ExpiryPolicy} or {@link CacheLoader}.
 *
 * @author Jens Wilke
 */
public class CustomizationException extends CacheException {

  public CustomizationException(String message) {
    super(message);
  }

  public CustomizationException(Throwable cause) {
    super(cause);
  }

  public CustomizationException(String message, Throwable cause) {
    super(message, cause);
  }

}
