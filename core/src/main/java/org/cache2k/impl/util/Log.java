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

import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * cache2k has only sparse logging. The direct use of java.util.logging was
 * examined. However, a thread name is missing within the output and it
 * is not properly recorded. To have the chance to redirect any
 * logging to the framework of choice, the log output is channeled
 * through this class.
 *
 * <p/>To hook in another log framework provide another LogFactory
 * implementation via the service loader.
 *
 * @author Jens Wilke; created: 2014-04-27
 * @see ServiceLoader
 */
public abstract class Log {

  static WeakHashMap<String, Log> loggers = new WeakHashMap<String, Log>();

  static LogFactory logFactory;

  public static Log getLog(Class<?> _class) {
    return getLog(_class.getName());
  }

  public static synchronized Log getLog(String s) {
    Log l = loggers.get(s);
    if (l != null) {
      return l;
    }
    if (logFactory != null) {
      l = logFactory.getLog(s);
      loggers.put(s, l);
      return l;
    }
    ServiceLoader<LogFactory> loader = ServiceLoader.load(LogFactory.class);
    for (LogFactory lf : loader) {
      logFactory = lf;
      getLog(Log.class.getName()).debug("New instance, using: " + logFactory);
      return getLog(s);
    }
    try {
      final org.apache.commons.logging.LogFactory cl =
        org.apache.commons.logging.LogFactory.getFactory();
      logFactory = new LogFactory() {
        @Override
        public Log getLog(String s) {
          return new CommonsLogger(cl.getInstance(s));
        }
      };
      getLog(Log.class.getName()).debug("New instance, using: " + logFactory);
      return getLog(s);
    } catch (NoClassDefFoundError ignore) {
    }
    logFactory = new LogFactory() {
      @Override
      public Log getLog(String s) {
        return new JdkLogger(Logger.getLogger(s));
      }
    };
    getLog(Log.class.getName()).debug("New instance, using: " + logFactory);
    return getLog(s);
  }

  /**
   * Redirects log output, this is used for testing purposes.
   */
  public static synchronized void registerSuppression(String s, Log l) {
    loggers.put(s, l);
  }

  public static synchronized void unregisterSuppression(String s) {
    loggers.remove(s);
  }

  public abstract boolean isDebugEnabled();

  public abstract boolean isInfoEnabled();

  public abstract void debug(String s);

  public abstract void debug(String s, Throwable ex);

  public abstract void info(String s);

  public abstract void info(String s, Throwable ex);

  public abstract void warn(String s);

  public abstract void warn(String s, Throwable ex);

  private static class CommonsLogger extends Log {

    org.apache.commons.logging.Log cLog;

    private CommonsLogger(org.apache.commons.logging.Log cLog) {
      this.cLog = cLog;
    }

    @Override
    public boolean isDebugEnabled() {
      return cLog.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
      return cLog.isInfoEnabled();
    }

    @Override
    public void debug(String s) {
      cLog.debug(s);
    }

    @Override
    public void debug(String s, Throwable ex) {
      cLog.debug(s, ex);
    }

    @Override
    public void info(String s, Throwable ex) {
      cLog.info(s);
    }

    @Override
    public void info(String s) {
      cLog.info(s);
    }

    @Override
    public void warn(String s) {
      cLog.warn(s);
    }

    @Override
    public void warn(String s, Throwable ex) {
      cLog.warn(s, ex);
    }
  }

  private static class JdkLogger extends Log {

    Logger logger;

    private JdkLogger(Logger logger) {
      this.logger = logger;
    }

    @Override
    public boolean isDebugEnabled() {
      return logger.isLoggable(Level.FINE);
    }

    @Override
    public boolean isInfoEnabled() {
      return logger.isLoggable(Level.INFO);
    }

    @Override
    public void debug(String s) {
      logger.log(Level.FINE, s);
    }

    @Override
    public void debug(String s, Throwable ex) {
      logger.log(Level.FINE, s, ex);
    }

    @Override
    public void info(String s) {
      logger.log(Level.INFO, s);
    }

    @Override
    public void info(String s, Throwable ex) {
      logger.log(Level.INFO, s, ex);
    }

    @Override
    public void warn(String s) {
      logger.log(Level.WARNING, s);
    }

    @Override
    public void warn(String s, Throwable ex) {
      logger.log(Level.WARNING, s, ex);
    }
  }

  /**
   * Log implementation that can be used to count suppressed log outputs.
   */
  public static class SuppressionCounter extends Log {

    AtomicInteger debugCount = new AtomicInteger();
    AtomicInteger infoCount = new AtomicInteger();
    AtomicInteger warnCount = new AtomicInteger();

    @Override
    public boolean isDebugEnabled() {
      return true;
    }

    @Override
    public boolean isInfoEnabled() {
      return true;
    }

    @Override
    public void debug(String s) {
      debugCount.incrementAndGet();
    }

    @Override
    public void debug(String s, Throwable ex) {
      debugCount.incrementAndGet();
    }

    @Override
    public void info(String s, Throwable ex) {
      infoCount.incrementAndGet();
    }

    @Override
    public void info(String s) {
      infoCount.incrementAndGet();
    }

    @Override
    public void warn(String s) {
      warnCount.incrementAndGet();
    }

    @Override
    public void warn(String s, Throwable ex) {
      warnCount.incrementAndGet();
    }

    public int getDebugCount() {
      return debugCount.get();
    }

    public int getInfoCount() {
      return infoCount.get();
    }

    public int getWarnCount() {
      return warnCount.get();
    }
  }

}
