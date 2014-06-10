package org.cache2k.storage;

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
 * @author Jens Wilke; created: 2014-03-27
 */
public interface StorageEntry {

  /** Key of the stored entry */
  Object getKey();

  /** Value of the stored entry */
  Object getValueOrException();

  /** Time the entry was last fetched or created from the original source */
  long getCreatedOrUpdated();

  /** Time when the entry is expired and needs to be refreshed. Is 0 if not used. */
  long getExpiryTime();

  /**
   * Time of last access in millis. Needed, if there is a idle expiry
   * configured. Is 0 if not used.
   */
  long getLastUsed();

  /**
   * Milliseconds after last used time after this entry gets expired.
   * Is 0 if not used.
   */
  long getMaxIdleTime();

}
