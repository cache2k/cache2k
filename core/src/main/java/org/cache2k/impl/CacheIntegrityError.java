package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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

/**
* @author Jens Wilke; created: 2013-07-14
*/
public class CacheIntegrityError extends Error {

  String cacheStatistics;
  String failingChecksString;
  String stateDescriptor;

  public CacheIntegrityError(
          String stateDescriptor,
          String failingChecksString,
          String cacheStatistics) {
    super(stateDescriptor + ", " + failingChecksString + ", " + cacheStatistics);
    this.cacheStatistics = cacheStatistics;
    this.failingChecksString = failingChecksString;
    this.stateDescriptor = stateDescriptor;
  }

  public String getCacheStatistics() {
    return cacheStatistics;
  }

  public String getFailingChecksString() {
    return failingChecksString;
  }

  public String getStateDescriptor() {
    return stateDescriptor;
  }

}
