package org.cache2k.jcache.event;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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

import org.cache2k.CacheEntry;

import javax.cache.Cache;
import javax.cache.event.EventType;

/**
 * Entry event with original value for update events.
 *
 * @author Jens Wilke
 */
public class EntryEventWithOldValue<K, V> extends EntryEvent<K, V> {

  V oldValue;

  public EntryEventWithOldValue(final Cache source, final EventType eventType, final CacheEntry<K, V> _c2kEntry, final V _oldValue) {
    super(source, eventType, _c2kEntry);
    oldValue = _oldValue;
  }

  public EntryEventWithOldValue(final Cache source, final EventType eventType, final K _key, final V _value, final V _oldValue) {
    super(source, eventType, _key, _value);
    oldValue = _oldValue;
  }

  @Override
  public V getOldValue() {
    return oldValue;
  }

  @Override
  public boolean isOldValueAvailable() {
    return true;
  }

}
