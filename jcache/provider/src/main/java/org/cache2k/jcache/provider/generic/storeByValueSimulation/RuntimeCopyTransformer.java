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

import java.io.Serializable;

/**
 * @author Jens Wilke
 */
public class RuntimeCopyTransformer extends CopyTransformer<Object>  {

  @Override
  public Object copy(Object obj) {
    if (obj == null) {
      return null;
    }
    if (SimpleObjectCopyFactory.isImmutable(obj.getClass())) {
      return obj;
    }
    if (obj instanceof Serializable) {
      return SerializableCopyTransformer.copySerializableObject(obj);
    }
    throw new IllegalArgumentException("Cannot determine copy / marshalling method for: " + obj.getClass().getName());
  }

}
