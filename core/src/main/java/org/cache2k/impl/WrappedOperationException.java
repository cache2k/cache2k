package org.cache2k.impl;

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

import org.cache2k.CacheException;

/**
 * Wraps an exception that happened during processing of one entry, e.g. when the
 * {@link org.cache2k.EntryExpiryCalculator} was throwing it. For async operations
 * these can happen on other threads, so we consequently wrap them.
 *
 * @author Jens Wilke
 */
public final class WrappedOperationException extends CacheException {
  public WrappedOperationException(final Throwable cause) {
    super(cause);
  }
}
