package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

import java.io.Closeable;
import java.util.Iterator;

/**
 * After the usage of the iterator close should be called to free resources.
 * The cache also alters the hash table operations for the life of the iterator
 * and switches back to normal after the iteration is finished.
 *
 * @author Jens Wilke; created: 2014-06-07
 */
public interface ClosableIterator<E> extends Iterator<E>, Closeable {

  /**
   * Immediately free resources held by the iterator. Overrides {@link java.io.Closeable#close()}
   * since no checked exceptions are thrown by the method.
   */
  @Override
  public void close();

}
