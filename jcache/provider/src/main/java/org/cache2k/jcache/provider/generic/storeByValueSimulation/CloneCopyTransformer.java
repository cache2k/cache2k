package org.cache2k.jcache.provider.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache JSR107 implementation
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
