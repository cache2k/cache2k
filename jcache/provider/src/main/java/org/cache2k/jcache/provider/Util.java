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

/**
 * @author Jens Wilke
 */
public class Util {

  private Util() {
  }

  public static <T> T checkKey(T _value) {
    return requireNonNull(_value, "cache key");
  }

  public static <T> T checkValue(T _value) {
    return requireNonNull(_value, "cache value");
  }

  public static <T> T requireNonNull(T _value, String _parameter) {
    if (_value == null) {
      throw new NullPointerException("null not allowed for: " + _parameter);
    }
    return _value;
  }

}
