package org.cache2k.impl.xmlConfiguration;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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

import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CustomizationSupplierByClassName;
import org.cache2k.core.spi.CacheConfigurationProvider;
import org.cache2k.impl.xmlConfiguration.generic.ConfigurationException;
import org.cache2k.impl.xmlConfiguration.generic.ConfigurationParser;
import org.cache2k.impl.xmlConfiguration.generic.ConfigurationTokenizer;
import org.cache2k.impl.xmlConfiguration.generic.FlexibleXmlTokenizerFactory;
import org.cache2k.impl.xmlConfiguration.generic.ParsedConfiguration;
import org.cache2k.impl.xmlConfiguration.generic.StandardVariableExpander;
import org.cache2k.impl.xmlConfiguration.generic.TokenizerFactory;
import org.cache2k.impl.xmlConfiguration.generic.Util;
import org.cache2k.impl.xmlConfiguration.generic.VariableExpander;
import org.cache2k.jcache.JCacheConfiguration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hooks into cache2k and provides the additional configuration data.
 *
 * @author Jens Wilke
 */
public class CacheConfigurationProviderImpl
  extends ConfigurationProvider implements CacheConfigurationProvider {

  private static final String DEFAULT_CONFIGURATION_FILE = "cache2k.xml";
  private static final Map<String, String> version1SectionTypes = new HashMap<String, String>() {
    {
      put("jcache", JCacheConfiguration.class.getName());
      put("byClassName", CustomizationSupplierByClassName.class.getName());
    }
  };

  private TokenizerFactory tokenizerFactory = new FlexibleXmlTokenizerFactory();
  private volatile Map<CacheManager, ConfigurationContext> manager2defaultConfig =
    new HashMap<CacheManager, ConfigurationContext>();
  private volatile Map<ClassLoader, ConfigurationContext> classLoader2config =
    new HashMap<ClassLoader, ConfigurationContext>();

  /**
   * The name of the default manager may be changed in the configuration file.
   * Load the default configuration file and save the loaded context for the respective
   * classloader, so we do not load the context twice when we create the first cache.
   */
  @Override
  public String getDefaultManagerName(ClassLoader cl) {
    ConfigurationContext ctx = classLoader2config.get(cl);
    if (ctx == null) {
      ctx = createContext(cl, null, DEFAULT_CONFIGURATION_FILE);
      Map<ClassLoader, ConfigurationContext> m2 = new HashMap<ClassLoader, ConfigurationContext>(classLoader2config);
      m2.put(cl, ctx);
      classLoader2config = m2;
    }
    return ctx.getManagerConfiguration().getDefaultManagerName();
  }

  @Override
  public Cache2kConfiguration getDefaultConfiguration(final CacheManager mgr) {
    Cache2kConfiguration cfg = getManagerContext(mgr).getDefaultManagerConfiguration();
    try {
      return Util.copyViaSerialization(cfg);
    } catch (Exception ex) {
      throw new ConfigurationException("Copying default cache configuration for manager '" + mgr.getName() + "'", ex);
    }
  }

  @Override
  public <K, V> void augmentConfiguration(final CacheManager mgr, final Cache2kConfiguration<K, V> cfg) {
    ConfigurationContext ctx =  getManagerContext(mgr);
    if (!ctx.isConfigurationPresent()) {
      return;
    }
    final String cacheName = cfg.getName();
    if (cacheName == null) {
      if (ctx.getManagerConfiguration().isIgnoreAnonymousCache()) {
        return;
      }
      throw new ConfigurationException(
        "Cache name missing, cannot apply XML configuration. " +
        "Consider parameter: ignoreAnonymousCache");
    }
    ParsedConfiguration _parsedTop = readManagerConfigurationWithExceptionHandling(mgr.getClassLoader(), getFileName(mgr));
    ParsedConfiguration _section = extractCachesSection(_parsedTop);
    ParsedConfiguration _parsedCache = null;
    if (_section != null) { _parsedCache = _section.getSection(cacheName); }
    if (_parsedCache == null) {
      if (ctx.getManagerConfiguration().isIgnoreMissingCacheConfiguration()) {
        return;
      }
      String _exceptionText =
        "Configuration for cache '" + cacheName + "' is missing. " +
          "Consider parameter: ignoreMissingCacheConfiguration" +
          " at " + _parsedTop.getSource();
      throw new IllegalArgumentException(_exceptionText);
    }
    apply(ctx, _parsedCache, cfg);
  }

  @Override
  public Iterable<String> getConfiguredCacheNames(final CacheManager mgr) {
    ConfigurationContext ctx =  getManagerContext(mgr);
    if (!ctx.isConfigurationPresent()) {
      return Collections.emptyList();
    }
    ParsedConfiguration parsedTop = readManagerConfigurationWithExceptionHandling(mgr.getClassLoader(), getFileName(mgr));
    ParsedConfiguration section = extractCachesSection(parsedTop);
    if (section == null) {
      return Collections.emptyList();
    }
    List<String> names = new ArrayList<String>();
    for (ParsedConfiguration pc : section.getSections()) {
      names.add(pc.getName());
    }
    return names;
  }

  private static String getFileName(CacheManager mgr) {
    if (mgr.isDefaultManager()) {
      return DEFAULT_CONFIGURATION_FILE;
    }
    return "cache2k-" + mgr.getName() + ".xml";
  }

  private ParsedConfiguration readManagerConfiguration(ClassLoader cl, final String fileName) throws Exception {
    InputStream is = cl.getResourceAsStream(fileName);
    if (is == null) {
      return null;
    }
    ConfigurationTokenizer tkn = tokenizerFactory.createTokenizer(fileName, is, null);
    ParsedConfiguration cfg = ConfigurationParser.parse(tkn);
    is.close();
    VariableExpander expander = new StandardVariableExpander();
    expander.expand(cfg);
    return cfg;
  }

  private ParsedConfiguration extractCachesSection(ParsedConfiguration pc) {
    ParsedConfiguration cachesSection = pc.getSection("caches");
    if (cachesSection == null) {
      return null;
    }
   return cachesSection;
  }

  private void checkCacheConfigurationOnStartup(final ConfigurationContext ctx, final ParsedConfiguration pc) {
    ParsedConfiguration cachesSection = pc.getSection("caches");
    if (cachesSection == null) {
      return;
    }
    for (ParsedConfiguration cacheConfig : cachesSection.getSections()) {
      apply(ctx, cacheConfig, new Cache2kConfiguration());
    }
  }

  /**
   * Hold the cache default configuration of a manager in a hash table. This is reused for all caches of
   * one manager.
   */
  private ConfigurationContext getManagerContext(final CacheManager mgr) {
    ConfigurationContext ctx = manager2defaultConfig.get(mgr);
    if (ctx != null) {
      return ctx;
    }
    synchronized (this) {
      ctx = manager2defaultConfig.get(mgr);
      if (ctx != null) {
        return ctx;
      }
      if (mgr.isDefaultManager()) {
        ctx = classLoader2config.get(mgr.getClassLoader());
      }
      if (ctx == null) {
        ctx = createContext(mgr.getClassLoader(), mgr.getName(), getFileName(mgr));
      }
      Map<CacheManager, ConfigurationContext> m2 =
        new HashMap<CacheManager, ConfigurationContext>(manager2defaultConfig);
      m2.put(mgr, ctx);
      manager2defaultConfig = m2;
      return ctx;
    }
  }

  private ConfigurationContext createContext(ClassLoader cl, String managerName, String fileName) {
    ParsedConfiguration pc = readManagerConfigurationWithExceptionHandling(cl, fileName);
    ConfigurationContext ctx = new ConfigurationContext();
    ctx.setClassLoader(cl);
    Cache2kConfiguration defaultConfiguration = new Cache2kConfiguration();
    ctx.setDefaultManagerConfiguration(defaultConfiguration);
    ctx.getManagerConfiguration().setDefaultManagerName(managerName);
    if (pc != null) {
      defaultConfiguration.setExternalConfigurationPresent(true);
      ctx.setTemplates(extractTemplates(pc));
      apply(ctx, pc, ctx.getManagerConfiguration());
      if (ctx.getManagerConfiguration().getVersion() != null && ctx.getManagerConfiguration().getVersion().startsWith("1.")) {
        ctx.setPredefinedSectionTypes(version1SectionTypes);
      }
      applyDefaultConfigurationIfPresent(ctx, pc, defaultConfiguration);
      ctx. setConfigurationPresent(true);
      if (!ctx.getManagerConfiguration().isSkipCheckOnStartup()) {
        checkCacheConfigurationOnStartup(ctx, pc);
      }
    }
    return ctx;
  }

  private ParsedConfiguration readManagerConfigurationWithExceptionHandling(final ClassLoader cl, final String fileName) {
    ParsedConfiguration pc;
    try {
      pc = readManagerConfiguration(cl, fileName);
    } catch (CacheException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ConfigurationException("Reading configuration for manager from '" + fileName + "'", ex);
    }
    return pc;
  }

  private void applyDefaultConfigurationIfPresent(final ConfigurationContext ctx,
                                                  final ParsedConfiguration pc,
                                                  final Cache2kConfiguration defaultConfiguration) {
    ParsedConfiguration defaults = pc.getSection("defaults");
    if (defaults != null) {
      defaults = defaults.getSection("cache");
    }
    if (defaults != null) {
      apply(ctx, defaults, defaultConfiguration);
    }
  }

  private ParsedConfiguration extractTemplates(final ParsedConfiguration pc) {
    return pc.getSection("templates");
  }

}
