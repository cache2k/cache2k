package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

import org.cache2k.CustomizationException;

/**
 * Exception happened in the resilience policy. This exception is usually propagated
 * by a CacheLoaderException and suppresses a load exception.
 *
 * @author Jens Wilke
 */
public class ResiliencePolicyException extends CustomizationException {

  public ResiliencePolicyException(final Throwable cause) {
    super("loader exception suppressed", cause);
  }

}
