package org.cache2k.impl.operation;

/*
 * #%L
 * cache2k core package
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
 * A entry on the heap cache, that may only be used for reading.
 * Only the relevant properties are defined to implement the cache
 * semantics on it.
 *
 * @author Jens Wilke
 */
public interface ExaminationEntry<K, V> {

  /** Associated key */
  K getKey();

  /** Associated value or the {@link org.cache2k.impl.ExceptionWrapper} */
  V getValueOrException();

  long getLastModification();

}
