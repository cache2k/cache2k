package org.cache2k;

/*
 * #%L
 * cache2k api only package
 * %%
 * Copyright (C) 2000 - 2013 headissue GmbH, Munich
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Simple interface to return a value selected by a key object. Cache users
 * of a read-through cache may choose this simple interface for requesting data
 * only, rather to use the full blown cache interface.
 *
 * @author Jens Wilke; created: 2013-12-17
 */
public interface KeyValueSource<K, T> extends CacheSource<K, T> {

  /**
   * Returns object mapped to key
   */
  public abstract @Nullable T get(@Nonnull K key);

}
