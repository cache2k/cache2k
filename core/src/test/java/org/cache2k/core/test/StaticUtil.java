package org.cache2k.core.test;

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

import org.cache2k.Cache;
import org.cache2k.impl.InternalCache;
import org.cache2k.impl.InternalCacheInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Jens Wilke
 */
public class StaticUtil {

  public static <T> Set<T> asSet(T... keys) {
    return new HashSet<T>(Arrays.asList(keys));
  }

  public static InternalCacheInfo latestInfo(Cache c) {
    return ((InternalCache) c).getLatestInfo();
  }

}
