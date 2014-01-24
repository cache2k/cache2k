package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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
 * Indicates cache missusage including configuration error.
 *
 * @author Jens Wilke; created: 2013-12-17
 */
public class CacheUsageExcpetion extends RuntimeException {

  public CacheUsageExcpetion() {
  }

  public CacheUsageExcpetion(String message) {
    super(message);
  }

  public CacheUsageExcpetion(String message, Throwable cause) {
    super(message, cause);
  }

  public CacheUsageExcpetion(Throwable cause) {
    super(cause);
  }

}
