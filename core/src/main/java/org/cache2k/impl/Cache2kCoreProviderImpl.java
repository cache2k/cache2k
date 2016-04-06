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

import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.Cache2kManagerProvider;

/**
 * @author Jens Wilke; created: 2014-04-20
 */
public class Cache2kCoreProviderImpl extends Cache2kCoreProvider {

  static Class<?> DEFAULT_STORAGE_IMPLEMENTATION;


  Cache2kManagerProviderImpl provider;

  @Override
  public synchronized Cache2kManagerProvider getManagerProvider() {
    if (provider == null) {
      provider = new Cache2kManagerProviderImpl();
    }
    return provider;
  }

  @Override
  public Class<CacheBuilderImpl> getBuilderImplementation() {
    return CacheBuilderImpl.class;
  }

  @Override
  public Class<?> getDefaultPersistenceStoreImplementation() {
    return DEFAULT_STORAGE_IMPLEMENTATION;
  }

}
