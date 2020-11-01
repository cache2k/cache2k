package org.cache2k.extra.config.provider;

/*
 * #%L
 * cache2k config file support
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

import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CustomizationSupplierByClassName;
import org.cache2k.core.spi.CacheConfigProvider;
import org.cache2k.extra.config.generic.ConfigurationException;
import org.cache2k.extra.config.generic.ConfigurationParser;
import org.cache2k.extra.config.generic.ConfigurationTokenizer;
import org.cache2k.extra.config.generic.FlexibleXmlTokenizerFactory;
import org.cache2k.extra.config.generic.ParsedConfiguration;
import org.cache2k.extra.config.generic.StandardVariableExpander;
import org.cache2k.extra.config.generic.TokenizerFactory;
import org.cache2k.extra.config.generic.Util;
import org.cache2k.extra.config.generic.VariableExpander;

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
@SuppressWarnings("rawtypes")
public class CacheConfigProviderImpl
  extends ConfigurationProvider implements CacheConfigProvider {

  private static final String DEFAULT_CONFIGURATION_FILE = "cache2k.xml";
  private static final Map<String, String> VERSION_1_2_SECTION_TYPES = new HashMap<String, String>() {
    {
      put("jcache", "org.cache2k.jcache.JCacheConfig");
      put("byClassName", CustomizationSupplierByClassName.class.getName());
      put("resilience", "org.cache2k.addon.UniversalResilienceConfig");
    }
  };

  private final TokenizerFactory tokenizerFactory = new FlexibleXmlTokenizerFactory();
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
      Map<ClassLoader, ConfigurationContext> m2 =
        new HashMap<ClassLoader, ConfigurationContext>(classLoader2config);
      m2.put(cl, ctx);
      classLoader2config = m2;
    }
    return ctx.getManagerConfiguration().getDefaultManagerName();
  }

  @Override
  public Cache2kConfig getDefaultConfig(CacheManager mgr) {
    Cache2kConfig cfg = getManagerContext(mgr).getDefaultManagerConfiguration();
    try {
      return Util.copyViaSerialization(cfg);
    } catch (Exception ex) {
      throw new ConfigurationException(
        "Copying default cache configuration for manager '" + mgr.getName() + "'", ex);
    }
  }

  @Override
  public <K, V> void augmentConfig(CacheManager mgr, Cache2kConfig<K, V> cfg) {
    ConfigurationContext ctx =  getManagerContext(mgr);
    if (!ctx.isConfigurationPresent()) {
      return;
    }
    String cacheName = cfg.getName();
    if (cacheName == null) {
      if (ctx.getManagerConfiguration().isIgnoreAnonymousCache()) {
        return;
      }
      throw new ConfigurationException(
        "Cache name missing, cannot apply XML configuration. " +
        "Consider parameter: ignoreAnonymousCache");
    }
    ParsedConfiguration parsedTop =
      readManagerConfigurationWithExceptionHandling(mgr.getClassLoader(), getFileName(mgr));
    ParsedConfiguration section = extractCachesSection(parsedTop);
    ParsedConfiguration parsedCache = null;
    if (section != null) { parsedCache = section.getSection(cacheName); }
    if (parsedCache == null) {
      if (ctx.getManagerConfiguration().isIgnoreMissingCacheConfiguration()) {
        return;
      }
      String exceptionText =
        "Configuration for cache '" + cacheName + "' is missing. " +
          "Consider parameter: ignoreMissingCacheConfiguration" +
          " at " + parsedTop.getSource();
      throw new IllegalArgumentException(exceptionText);
    }
    apply(ctx, parsedCache, cfg);
  }

  @Override
  public Iterable<String> getConfiguredCacheNames(CacheManager mgr) {
    ConfigurationContext ctx =  getManagerContext(mgr);
    if (!ctx.isConfigurationPresent()) {
      return Collections.emptyList();
    }
    ParsedConfiguration parsedTop =
      readManagerConfigurationWithExceptionHandling(mgr.getClassLoader(), getFileName(mgr));
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

  private ParsedConfiguration readManagerConfiguration(ClassLoader cl, String fileName)
    throws Exception {
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
    return cachesSection;
  }

  private void checkCacheConfigurationOnStartup(ConfigurationContext ctx, ParsedConfiguration pc) {
    ParsedConfiguration cachesSection = pc.getSection("caches");
    if (cachesSection == null) {
      return;
    }
    for (ParsedConfiguration cacheConfig : cachesSection.getSections()) {
      apply(ctx, cacheConfig, new Cache2kConfig());
    }
  }

  /**
   * Hold the cache default configuration of a manager in a hash table. This is reused for all
   * caches of one manager.
   */
  private ConfigurationContext getManagerContext(CacheManager mgr) {
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
    Cache2kConfig defaultConfiguration = new Cache2kConfig();
    ctx.setDefaultManagerConfiguration(defaultConfiguration);
    ctx.getManagerConfiguration().setDefaultManagerName(managerName);
    if (pc != null) {
      defaultConfiguration.setExternalConfigurationPresent(true);
      ctx.setTemplates(extractTemplates(pc));
      apply(ctx, pc, ctx.getManagerConfiguration());
      final String version = ctx.getManagerConfiguration().getVersion();
      if (version != null && (version.startsWith("1.") || version.startsWith("2."))) {
        ctx.setPredefinedSectionTypes(VERSION_1_2_SECTION_TYPES);
      }
      applyDefaultConfigurationIfPresent(ctx, pc, defaultConfiguration);
      ctx.setConfigurationPresent(true);
      if (!ctx.getManagerConfiguration().isSkipCheckOnStartup()) {
        checkCacheConfigurationOnStartup(ctx, pc);
      }
    }
    return ctx;
  }

  private ParsedConfiguration readManagerConfigurationWithExceptionHandling(ClassLoader cl,
                                                                            String fileName) {
    ParsedConfiguration pc;
    try {
      pc = readManagerConfiguration(cl, fileName);
    } catch (CacheException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ConfigurationException(
        "Reading configuration for manager from '" + fileName + "'", ex);
    }
    return pc;
  }

  private void applyDefaultConfigurationIfPresent(ConfigurationContext ctx,
                                                  ParsedConfiguration pc,
                                                  Cache2kConfig defaultConfiguration) {
    ParsedConfiguration defaults = pc.getSection("defaults");
    if (defaults != null) {
      defaults = defaults.getSection("cache");
    }
    if (defaults != null) {
      apply(ctx, defaults, defaultConfiguration);
    }
  }

  private ParsedConfiguration extractTemplates(ParsedConfiguration pc) {
    return pc.getSection("templates");
  }

}
