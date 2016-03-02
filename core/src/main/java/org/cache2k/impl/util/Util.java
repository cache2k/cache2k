package org.cache2k.impl.util;

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

import java.sql.Timestamp;

/**
 * A set of utility stuff we need often.
 *
 * @author Jens Wilke; created: 2014-12-18
 */
public class Util {

  private Util() {
  }

  public static String formatMillis(long _millis) {
     return new Timestamp(_millis).toString();
  }

  /**
   * Always throws exception. Used to mark a place in the code that needs work.
   *
   * @throws UnsupportedOperationException
   */
  public static void TODO() {
    throw new UnsupportedOperationException("TODO");
  }
  
}
