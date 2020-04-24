package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache provider
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
 * Copy: Transform between identical representation types, but different instances.
 *
 * @author Jens Wilke
 */
public abstract class CopyTransformer<T> implements ObjectTransformer<T, T> {

  @Override
  public T compact(T _external) {
    return copy(_external);
  }

  @Override
  public T expand(T _internal) {
    return copy(_internal);
  }

  protected abstract T copy(T o);

}
