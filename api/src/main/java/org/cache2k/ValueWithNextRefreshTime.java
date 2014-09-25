package org.cache2k;

/*
 * #%L
 * cache2k API only package
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
 * Interface for use with {@link ValueRefreshController}
 *
 * @author Jens Wilke; created: 2013-05-02
 * @see ValueRefreshController
 */
public interface ValueWithNextRefreshTime {

  /**
   * Return time of next refresh (expiry time). A return value of 0 means the
   * entry expires immediately, or is always fetched from the source. A return value of
   * {@link Long#MAX_VALUE} means there is no specific expiry time
   * known or needed. In this case a reasonable default can be assumed for
   * the expiry, the cache will use the configured expiry time.
   */
  public long getNextRefreshTime();

}
