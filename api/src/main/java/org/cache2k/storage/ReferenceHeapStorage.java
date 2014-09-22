package org.cache2k.storage;

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

import org.cache2k.RootAnyBuilder;
import org.cache2k.spi.StorageImplementation;
import org.cache2k.spi.VoidConfigBuilder;

/**
 * Storage which keeps the data in a simple hash map in the heap.
 *
 * @author Jens Wilke; created: 2014-06-21
 */
public interface ReferenceHeapStorage<R extends RootAnyBuilder<R, T>, T>
  extends StorageImplementation<R, T, VoidConfigBuilder<R, T>, Void> {

}
