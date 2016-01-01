package org.cache2k.jcache.generic.storeByValueSimulation;

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

/**
 * @author Jens Wilke
 */
public interface ObjectTransformer<E, I> {

  public final static ObjectTransformer IDENT_TRANSFORM = new ObjectTransformer<Object, Object>() {
    @Override
    public Object expand(Object _internal) {
      return _internal;
    }

    @Override
    public Object compact(Object _external) {
      return _external;
    }
  };

  public final static ObjectTransformer SERIALIZABLE_COPY_TRANSFORM = new SerializableCopyTransformer();

  E expand(I _internal);
  I compact(E _external);

}
