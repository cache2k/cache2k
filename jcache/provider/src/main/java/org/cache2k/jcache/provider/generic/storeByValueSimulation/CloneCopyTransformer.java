package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import javax.cache.CacheException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Implement copy via reflective call to a clone method (needs to be public).
 *
 * @author Jens Wilke
 */
public class CloneCopyTransformer<T> extends CopyTransformer<T> {                                 ;

  Method cloneMethod;

  public CloneCopyTransformer(Method cloneMethod) {
    this.cloneMethod = cloneMethod;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected T copy(T o) {
    if (o == null) {
      return null;
    }
    try {
      return (T) cloneMethod.invoke(o);
    } catch (IllegalAccessException e) {
      throw new CacheException("Failure to copy via clone", e);
    } catch (InvocationTargetException e) {
      throw new CacheException("Failure to copy via clone", e);
    }
  }

}
