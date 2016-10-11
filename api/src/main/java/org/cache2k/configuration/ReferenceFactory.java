package org.cache2k.configuration;

/*
 * #%L
 * cache2k API
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

import org.cache2k.CacheManager;

/**
 * A reference to the customization to be used is set while building the cache.
 * The reference is returned. The class loader can be ignored.
 *
 * @author Jens Wilke
 */
public class ReferenceFactory<T> implements CustomizationFactory<T> {

  private T object;

  public ReferenceFactory(final T _object) {
    object = _object;
  }

  @Override
  public T create(final CacheManager ignored) {
    return object;
  }

}
