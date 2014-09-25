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
 *
 *
 * B type of the root builder
 * P parent builder
 *
 */
public interface AnyBuilder<K, T, C> {

  /**
   * Create the configuration of this submodule (not the complete
   * configuration).
   */
  C createConfiguration();

  /**
   * Goes back to the root builder, to add more configuration nodes.
   */
  CacheBuilder<K, T> root();

  /**
   * Builds the instance which is the target of nested builders. This
   * is either a {@link Cache} or a {@link CacheConfig}. The method is
   * always identical to root().build().
   */
  Cache<K, T> build();

}
