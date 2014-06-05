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
 */
public abstract class BaseAnyBuilder<T, C> {

  protected RootAnyBuilder<T> root;

  public StorageConfiguration.Builder<T> addPersistence() { return root.addPersistence(); }

  public StorageConfiguration.Builder<T> addStore() { return root.addStore();}

  public abstract C createConfiguration();

  public RootAnyBuilder<T> root() {
    return root;
  }

  public T build() {
    return root.build();
  }

  void setRoot(RootAnyBuilder<T> root) {
    this.root = root;
  }

}
