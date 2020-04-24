package org.cache2k.core.util;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import java.io.InputStream;
import java.util.Properties;

/**
 * Static helper class to provide the cache2k version.
 *
 * @author Jens Wilke
 */
public class Cache2kVersion {

  private static String version = "<unknown>";

  static {
    InputStream in = Cache2kVersion.class.getResourceAsStream("/org.cache2k.impl.version.txt");
    parse(in);
  }

  static void parse(InputStream in) {
    if (in != null) {
      try {
        Properties p = new Properties();
        p.load(in);
        in.close();
        String s = p.getProperty("version");
        if (isDefined(s)) {
          version = s;
        }
      } catch (Exception e) {
        Log.getLog(Cache2kVersion.class).warn("error parsing version properties", e);
      }
    }
  }

  static boolean isDefined(String s) {
    return s != null && s.length() > 0 && !s.startsWith("$");
  }

  public static String getVersion() {
    return version;
  }

}
