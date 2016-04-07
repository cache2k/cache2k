package org.cache2k.impl.util;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
