package org.cache2k;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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
 * A misconfiguration upon cache initialization is detected.
 * The cache is unable to operate.
 *
 * @author Jens Wilke; created: 2014-08-17
 */
public class CacheMisconfigurationException extends CacheException {

  public CacheMisconfigurationException() {
    super();
  }

  public CacheMisconfigurationException(String message) {
    super(message);
  }

  public CacheMisconfigurationException(String message, Throwable cause) {
    super(message, cause);
  }

  public CacheMisconfigurationException(Throwable cause) {
    super(cause);
  }

  public CacheMisconfigurationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

}
