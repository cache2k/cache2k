package org.cache2k.core.util;

/*
 * #%L
 * cache2k core implementation
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

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
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
 * <p>To hook in another log framework provide another LogFactory
 * implementation via the service loader.
 *
 * @author Jens Wilke
 */
public abstract class Log {

  private static final Map<String, Log> LOGGERS = new HashMap<String, Log>();

  private static final LogFactory LOG_FACTORY;

  /*
   * Initialize used log implementation statically for GraalVM
   */
  static {
    LOG_FACTORY = initializeLogFactory();
    Log log = getLog(Log.class.getName());
    log.debug("Using " + log.getClass().getSimpleName());
  }

  public static Log getLog(Class<?> type) {
    return getLog(type.getName());
  }

  public static synchronized Log getLog(String s) {
    Log l = LOGGERS.get(s);
    if (l != null) {
      return l;
    }
    if (LOG_FACTORY == null) {
      initializeLogFactory();
    }
    l = LOG_FACTORY.getLog(s);
    LOGGERS.put(s, l);
    return l;
  }

  /**
   * Finds a logger we can use. First we start with looking for a registered
   * service provider. Then apache commons logging. As a fallback we use JDK logging.
   */
  private static LogFactory initializeLogFactory() {
    ServiceLoader<LogFactory> loader = ServiceLoader.load(LogFactory.class);
    for (LogFactory lf : loader) {
      return lf;
    }
    try {
      final org.slf4j.ILoggerFactory lf = org.slf4j.LoggerFactory.getILoggerFactory();
      return new LogFactory() {
        @Override
        public Log getLog(String s) {
          return new Slf4jLogger(lf.getLogger(s));
        }
      };
    } catch (NoClassDefFoundError ignore) { }
    return new LogFactory() {
      @Override
      public Log getLog(String s) {
        return new JdkLogger(Logger.getLogger(s));
      }
    };
  }

  /**
   * Redirects log output, this is used for testing purposes. Needs to be called before the
   * log client is created.
   */
  public static synchronized void registerSuppression(String s, Log l) {
    LOGGERS.put(s, l);
  }

  public static synchronized void deregisterSuppression(String s) {
    LOGGERS.remove(s);
  }

  public abstract boolean isDebugEnabled();

  public abstract boolean isInfoEnabled();

  public abstract void debug(String s);

  public abstract void debug(String s, Throwable ex);

  public abstract void info(String s);

  public abstract void info(String s, Throwable ex);

  public abstract void warn(String s);

  public abstract void warn(String s, Throwable ex);

  private static class Slf4jLogger extends Log {

    org.slf4j.Logger logger;

    private Slf4jLogger(org.slf4j.Logger logger) {
      this.logger = logger;
    }

    @Override
    public boolean isDebugEnabled() {
      return logger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
      return logger.isInfoEnabled();
    }

    @Override
    public void debug(String s) {
      logger.debug(s);
    }

    @Override
    public void debug(String s, Throwable ex) {
      logger.debug(s, ex);
    }

    @Override
    public void info(String s, Throwable ex) {
      logger.info(s);
    }

    @Override
    public void info(String s) {
      logger.info(s);
    }

    @Override
    public void warn(String s) {
      logger.warn(s);
    }

    @Override
    public void warn(String s, Throwable ex) {
      logger.warn(s, ex);
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

    /**
     * Send the log message to the JDK logger. Using the <code>logp</code>
     * prevents the logger to derive the source class name from the call stack.
     */
    @Override
    public void debug(String s) {
      logger.logp(Level.FINE, null, null, s);
    }

    @Override
    public void debug(String s, Throwable ex) {
      logger.logp(Level.FINE, s, null, null, ex);
    }

    @Override
    public void info(String s) {
      logger.logp(Level.INFO, null, null, s);
    }

    @Override
    public void info(String s, Throwable ex) {
      logger.logp(Level.INFO, null, null, s, ex);
    }

    @Override
    public void warn(String s) {
      logger.logp(Level.WARNING, null, null, s);
    }

    @Override
    public void warn(String s, Throwable ex) {
      logger.logp(Level.WARNING, null, null, s, ex);
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
