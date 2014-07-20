package org.cache2k.impl.util;

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

import org.cache2k.impl.CacheInternalError;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Provides an instance of a tunable after applying changes taken
 * from configuration file or system properties.
 *
 * @see TunableConstants
 * @author Jens Wilke; created: 2014-04-27
 */
public final class TunableFactory {

  static Log log = Log.getLog(TunableFactory.class);

  public final static String DEFAULT_TUNING_FILE_NAME =
    "/org/cache2k/default-tuning.properties";

  public final static String CUSTOM_TUNING_FILE_NAME =
    "/org/cache2k/tuning.properties";

  public final static String TUNE_MARKER = "org.cache2k.tuning";

  private static Map<Class<?>, Object> map;

  private static Properties defaultProperties;

  private static Properties customProperties;

  /**
   * Reload the tunable configuration from the system properties
   * and the configuration file.
   */
  public static synchronized void reload() {
    map = new HashMap<>();
    customProperties = loadFile(CUSTOM_TUNING_FILE_NAME);
    defaultProperties = loadFile(DEFAULT_TUNING_FILE_NAME);
  }

  static Properties loadFile(final String _fileName) {
    InputStream in =
      TunableConstants.class.getResourceAsStream(_fileName);
    if (in != null) {
      try {
        Properties p = new Properties();
        p.load(in);
        in.close();
        return p;
      } catch (IOException ex) {
        throw new CacheInternalError("tuning properties not readable", ex);
      }
    }
    return null;
  }

  public synchronized static <T extends TunableConstants> T get(Properties p, Class<T> c) {
    T cfg = getDefault(c);
    if (p != null
      && p.containsKey(TUNE_MARKER)
      && p.containsKey(cfg.getClass().getName() + ".tuning")) {
      cfg = (T) cfg.clone();
      apply(p, cfg);
    }
    return cfg;
  }

  public synchronized static <T extends TunableConstants> T get(Class<T> c) {
    return getDefault(c);
  }

  private static <T extends TunableConstants> T getDefault(Class<T> c) {
    if (map == null) {
      reload();
    }
    @SuppressWarnings("unchecked")
    T cfg = (T) map.get(c);
    if (cfg == null) {
      try {
        cfg = c.newInstance();
      } catch (Exception ex) {
        throw new CacheInternalError("cannot instantiate tunables", ex);
      }
      apply(defaultProperties, cfg);
      apply(customProperties, cfg);
      apply(System.getProperties(), cfg);
      map.put(c, cfg);
    }
    return cfg;
  }

  static void apply(Properties p, Object cfg) {
    if (p == null) {
      return;
    }
    String _propName = null;
    try {
      for (Field f : cfg.getClass().getFields()) {
        _propName =
          cfg.getClass().getName().replace('$', '.') + "." + f.getName();
        String o = p.getProperty(_propName);
        if (o != null) {
          if (f.getType() == Boolean.TYPE) {
            o = o.toLowerCase();
            if (
              "off".equals(o) ||
              "false".equals(o) ||
              "disable".equals(o)) {
              f.set(cfg, false);
            } else {
              f.set(cfg, true);
            }
            if (log.isDebugEnabled()) {
              log.debug(_propName + "=" + f.get(cfg));
            }
          } else if (f.getType() == Integer.TYPE) {
            f.set(cfg, Integer.valueOf(o));
            if (log.isDebugEnabled()) {
              log.debug(_propName + "=" + f.get(cfg));
            }
          } else if (f.getType() == String.class) {
            f.set(cfg, o);
            if (log.isDebugEnabled()) {
              log.debug(_propName + "=" + f.get(cfg));
            }
          } else if (f.getType().isInterface()) {
            Class<?> c = Class.forName(o);
            Object _instance = c.newInstance();
            f.set(cfg, _instance);
            if (log.isDebugEnabled()) {
              log.debug(_propName + "=" + c.getName());
            }
          } else {
            throw new CacheInternalError("no policy to change this tunable type");
          }
        }
      }
    } catch (Exception ex) {
      if (_propName != null) {
        throw new CacheInternalError("error applying tuning setup, property=" + _propName, ex);
      } else {
        throw new CacheInternalError("error applying tuning setup" , ex);
      }
    }
  }

}
