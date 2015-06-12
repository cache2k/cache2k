package org.cache2k.impl.util;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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
import java.util.Properties;

/**
 * Static helper class to provide the cache2k version.
 *
 * @author Jens Wilke; created: 2015-06-11
 */
public class Cache2kVersion {

  private static String buildNumber = "<unknown>";
  private static String version = "<unknown>";
  private static long timestamp = 0;

  static {
    InputStream in = Cache2kVersion.class.getResourceAsStream("/org.cache2k.impl.version.txt");
    try {
      if (in != null) {
        Properties p = new Properties();
        p.load(in);
        System.err.println(p);
        String s = p.getProperty("buildNumber");
        if (s != null && s.length() > 0) {
          buildNumber = s;
        }
        version = p.getProperty("version");
        s = p.getProperty("timestamp");
        if ( s != null && s.length() > 0 && !"${timestamp}".equals(s)) {
          timestamp = Long.parseLong(s);
        } else {
          timestamp = 4711;
        }
      }
    } catch (Exception e) {
      Log.getLog(Cache2kVersion.class).warn("error parsing version properties", e);
    }
  }

  public static String getBuildNumber() {
    return buildNumber;
  }

  public static String getVersion() {
    return version;
  }

  public static long getTimestamp() {
    return timestamp;
  }

}
