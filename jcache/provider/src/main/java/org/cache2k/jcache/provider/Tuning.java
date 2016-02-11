package org.cache2k.jcache.provider;

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

import org.cache2k.impl.util.TunableConstants;
import org.cache2k.impl.util.TunableFactory;

/**
 * Some global parameters for the cache2k JCache implementation.
 *
 * @author Jens Wilke
 */
public class Tuning extends TunableConstants {

  public static final Tuning GLOBAL = TunableFactory.get(Tuning.class);

  /**
   * Every access to an JMX value will flush the cache statistics. This is only needed for the TCK,
   * that it can check the correct counting.
   */
  public boolean flushStatisticsOnAccess = true;

  /**
   * Apply a "correction" to the statistics for the entity processor invokes. Needed for the TCK.
   */
  public boolean tweakStatisticsForEntityProcessor = true;

}
