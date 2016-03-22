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

/**
 * A listener implemented by the applications to get notifications after a
 * {@link Cache#loadAll} or (@link Cache#reload} has been completed.
 *
 * @author Jens Wilke
 */
public interface LoadCompletedListener {

  /** Signals the completion of a {@link Cache#loadAll} or (@link Cache#reload} operation. */
  void loadCompleted();

  /**
   * The operation could not completed, because of an error.
   *
   * <p>In current implementations, there is no condition which raises a call to this method.
   * Errors while loading a value, will be delayed and propagated when the respective key
   * is accessed. This is subject to the resilience configuration.
   *
   * <p>The method may be used in the future if some general failure condition during load.
   * Applications should propagate the exception properly and not only log it.
   */
  void loadException(Exception _exception);

}
