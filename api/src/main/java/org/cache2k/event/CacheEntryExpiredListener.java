package org.cache2k.event;

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

import org.cache2k.Cache;
import org.cache2k.CacheEntry;

/**
 * Listener called for an expired entry. An expiry event may not be sent if an
 * entry is refreshed before the expiry is detected. In this cache an update event is sent.
 *
 * <p>Expiry events are not yet completely implemented.
 *
 * @author Jens Wilke
 */
public interface CacheEntryExpiredListener<K, V> extends CacheEntryOperationListener<K,V> {

  void onEntryExpired(Cache<K,V> c, CacheEntry<K,V> e);

}
