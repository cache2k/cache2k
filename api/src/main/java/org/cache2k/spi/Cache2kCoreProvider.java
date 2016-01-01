package org.cache2k.spi;

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

import org.cache2k.CacheBuilder;

import java.util.ServiceLoader;

/**
 * For API internal use only. The cache2k implementation provides the
 * concrete implementations via this interface. Only one active cache2k
 * implementation is supported by the API.
 *
 * <p>Right now, there is only one implementation within the core package.
 * Maybe there will be stripped or extended implementations, or special
 * build implementations, e.g. for Android in the future.
 *
 * <p>This is for internal use by the API to locate the implementation.
 * Do not use or rely on this.
 *
 * @author Jens Wilke; created: 2014-04-20
 */
public abstract class Cache2kCoreProvider {

  public abstract Cache2kManagerProvider getManagerProvider();

  public abstract Class<? extends CacheBuilder> getBuilderImplementation();

  public abstract Class<?> getDefaultPersistenceStoreImplementation();

}
