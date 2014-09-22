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

import org.cache2k.RootAnyBuilder;
import org.cache2k.spi.StorageImplementation;
import org.cache2k.spi.VoidConfigBuilder;

/**
 * @author Jens Wilke; created: 2014-06-21
 */
public abstract class CacheStorageBaseWithVoidConfig<R extends RootAnyBuilder<R,T>, T>
  implements
    StorageImplementation<R, T, VoidConfigBuilder<R, T>, Void>,
    CacheStorageProvider<Void> {

  /** Needs sub classing */
  protected CacheStorageBaseWithVoidConfig() { }

  @Override
  public VoidConfigBuilder<R, T> createConfigurationBuilder(R _rootBuilder) {
    return new VoidConfigBuilder<>(_rootBuilder);
  }

}
