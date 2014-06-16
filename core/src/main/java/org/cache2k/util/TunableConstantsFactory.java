package org.cache2k.util;

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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Returns a tunables instance after applying changes. Configuration
 * changes will be taken from the system properties or a provided
 * by properties file.
 *
 * @see TunableConstants
 * @author Jens Wilke; created: 2014-04-27
 */
public final class TunableConstantsFactory {

  public final static String GLOBAL_TUNING_FILE_NAME = "/org/cache2k/tuning.properties";

  public final static String TUNE_MARKER = "org.cache2k.tuning";

  private static Map<Class<?>, Object> map;

  private static Properties globalProperties;

  /**
   * Reload the tunable configuration from the system properties
   * and the configuration file.
   */
  public static void reload() {
    map = new HashMap<>();
    globalProperties = null;
    InputStream in = TunableConstants.class.getResourceAsStream(GLOBAL_TUNING_FILE_NAME);
    if (in != null) {
      try {
        globalProperties = new Properties();
        globalProperties.load(in);
        in.close();
      } catch (IOException ex) {
        throw new IllegalArgumentException(ex);
      }
    }

  }

  public static <T extends TunableConstants> T get(Properties p, Class<T> c) {
    if (map == null) {
      reload();
    }
    T cfg;
    try {
      cfg = c.newInstance();
    } catch (Exception ex) {
      throw new IllegalArgumentException("cannot instantiate tunables", ex);
    }
    apply(globalProperties, cfg);
    apply(System.getProperties(), cfg);
    apply(p, cfg);
    return cfg;
  }

  public static <T extends TunableConstants> T get(Class<T> c) {
    if (map == null) {
      reload();
    }
    @SuppressWarnings("unchecked")
    T cfg = (T) map.get(c);
    if (cfg == null) {
      try {
        cfg = c.newInstance();
      } catch (Exception ex) {
        throw new IllegalArgumentException("cannot instantiate tunables", ex);
      }
      apply(globalProperties, cfg);
      apply(System.getProperties(), cfg);
      map.put(c, cfg);
    }
    return cfg;
  }

  static void apply(Properties p, Object cfg) {
    if (p == null) {
      return;
    }
    if (!p.containsKey(TUNE_MARKER)) {
      return;
    }
    if (!p.containsKey(cfg.getClass().getName() + ".tuning")) {
      return;
    }
    try {
      for (Field f : cfg.getClass().getFields()) {
        String _propName = cfg.getClass().getName() + "." + f.getName();
        String o = p.getProperty(_propName);
        if (o != null) {
          if (f.getType() == Boolean.TYPE) {
            f.set(cfg, Boolean.valueOf(o));
          }
          if (f.getType() == Integer.TYPE) {
            f.set(cfg, Integer.valueOf(o));
          }
          if (f.getType() == String.class) {
            f.set(cfg, o);
          }
        }
      }
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

}
