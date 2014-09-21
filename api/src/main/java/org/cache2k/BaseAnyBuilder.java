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
 * Base builder which defines top level methods
 * delegating to the root configuration builder.
 *
 * @author Jens Wilke; created: 2014-04-19
 * @param <T> build target type, either cache or configuration
 * @param <C> configuration bean type
 * @param <B> builder type, also defines build target type
 */
public abstract class BaseAnyBuilder<B, T, C> {

  protected RootAnyBuilder<B, T> root;

  /**
   * Adds persistence to the cache or returns a previous added persistence storage
   * configuration node. The persistence configuration is taken from a default
   * configuration. Ideally speaking requesting persistence means that the
   * cache contains data that is costly to reproduce and/or needs a big
   * amount of storage which is not available within the java heap.
   */
  public StorageConfiguration.Builder<B, T> persistence() { return root.persistence(); }

  public StorageConfiguration.Builder<B, T> addStore() { return root.addStore();}

  public abstract C createConfiguration();

  public RootAnyBuilder<B, T> root() {
    return root;
  }

  public T build() {
    return root.build();
  }

  void setRoot(RootAnyBuilder<B, T> root) {
    this.root = root;
  }

}
