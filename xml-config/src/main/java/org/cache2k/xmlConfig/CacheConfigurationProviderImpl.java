package org.cache2k.xmlConfig;

/*
 * #%L
 * cache2k XML configuration
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

import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.CacheMisconfigurationException;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.spi.CacheConfigurationProvider;
import org.cache2k.core.util.Log;
import org.cache2k.core.util.Util;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Hooks into cache2k and provides the additional configuration data.
 *
 * @author Jens Wilke
 */
public class CacheConfigurationProviderImpl implements CacheConfigurationProvider {

  private ApplyConfiguration applicant = new ApplyConfiguration();
  private final boolean usePullParser;
  private final Log log = Log.getLog(this.getClass());
  private volatile Map<CacheManager, ConfigurationContext> manager2defaultConfig = new HashMap<CacheManager, ConfigurationContext>();

  {
    boolean _usePullParser = false;
    try {
      XmlPullParser.class.toString();
      _usePullParser = true;
    } catch (Exception ex) { }
    usePullParser = _usePullParser;
  }

  @Override
  public Cache2kConfiguration getDefaultConfiguration(final CacheManager mgr) {
    try {
      return copyViaSerialization(mgr, getManagerContext(mgr).getDefaultManagerConfiguration());
    } catch (Exception ex) {
      throw new CacheMisconfigurationException(
        "Providing default configuration for manager '" + mgr.getName() + "'", ex);
    }
  }

  @Override
  public <K, V> void augmentConfiguration(final CacheManager mgr, final Cache2kConfiguration<K, V> _bean) {
    ParsedConfiguration pc = null;
    ConfigurationContext ctx =  getManagerContext(mgr);
    final String _cacheName = _bean.getName();
    if (_cacheName == null) {
      if (ctx.isIgnoreAnonymousCache()) {
        return;
      }
      throw new CacheMisconfigurationException("Cache name missing");
    }
    try {
      pc = readManagerConfiguration(mgr);
      if (pc == null) {
        return;
      }
    } catch (Exception ex) {
      throw new CacheMisconfigurationException(
        "Cache '" + Util.compactFullName(mgr, _cacheName) + "'", ex);
    }
    try {
      ParsedConfiguration _cacheCfg = extractCacheSection(pc).getSection(_cacheName);
      if (_cacheCfg == null) {
        if (ctx.isIgnoreMissingConfiguration()) {
          return;
        }
        throw new ConfigurationException("Configuration for cache '" + _cacheName + "' is missing", pc.getSource());
      }
      applicant.apply(_cacheCfg, extractTemplates(pc), _bean);
    } catch (CacheException ex) {
       throw ex;
     } catch (Exception ex) {
       throw new ConfigurationException("Cache '" + _cacheName + "'", pc.getSource());
     }
  }

  private ParsedConfiguration readManagerConfiguration(final CacheManager mgr) throws Exception {
    String _fileName = "/cache2k-" + mgr.getName() + ".xml";
    InputStream is = this.getClass().getResourceAsStream(_fileName);
    if (is == null) {
      return null;
    }
    ConfigurationTokenizer tkn;
    if (usePullParser) {
      tkn = new XppConfigTokenizer(_fileName, is, null);
    } else {
      tkn = new StaxConfigTokenizer(_fileName, is, null);
    }
    ParsedConfiguration cfg = ConfigurationParser.parse(tkn);
    VariableExpander _expander = new StandardVariableExpander();
    _expander.expand(cfg);
    return cfg;
  }

  private ParsedConfiguration extractCacheSection(ParsedConfiguration pc) {
    ParsedConfiguration _cachesSection = pc.getSection("caches");
    if (_cachesSection == null) {
      throw new ConfigurationException("Section 'caches' missing", pc.getSource(), -1);
    }
   return _cachesSection;
  }

  private void checkCacheConfigurationOnStartup(final CacheManager mgr, final ConfigurationContext ctx, final ParsedConfiguration pc) throws Exception {
    ParsedConfiguration _templates = extractTemplates(pc);
    ParsedConfiguration _cachesSection = extractCacheSection(pc);
    for (ParsedConfiguration _cacheConfig : _cachesSection.getSections()) {
      applicant.apply(_cacheConfig, _templates, new Cache2kConfiguration());
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
      ParsedConfiguration pc;
      try {
        pc = readManagerConfiguration(mgr);
      } catch (Exception ex) {
        throw new CacheMisconfigurationException(
          "Configuration for manager '" + mgr.getName() + "'", ex);
      }
      try {
        ctx = new ConfigurationContext();
        Cache2kConfiguration _bean = new Cache2kConfiguration();
        applicant.apply(pc.getSection("defaults").getSection("cache"), extractTemplates(pc), _bean);
        applicant.apply(pc, null, ctx);
        if (!ctx.isSkipCheckOnStartup()) {
          checkCacheConfigurationOnStartup(mgr, ctx, pc);
        }
        ctx.setDefaultManagerConfiguration(_bean);
        Map<CacheManager, ConfigurationContext> m2 =
          new HashMap<CacheManager, ConfigurationContext>(manager2defaultConfig);
        m2.put(mgr, ctx);
        return ctx;
      } catch (Exception ex) {
        throw new ConfigurationException( "Configuration for manager '" + mgr.getName() + "'", pc.getSource());
      }
    }
  }

  private static <T extends Serializable> T copyViaSerialization(CacheManager mgr, T obj) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(obj);
      oos.flush();
      ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
      return (T) new ObjectInputStream(bin).readObject();
    } catch (Exception ex) {
      throw new CacheMisconfigurationException(
        "Copying default configuration for manager '" + mgr.getName() + "'");
    }
  }

  private ParsedConfiguration extractTemplates(final ParsedConfiguration _pc) {
    return _pc.getSection("templates");
  }

  static class ConfigurationContext {

    private String version = "1.0";
    private String managerName = null;
    private boolean ignoreMissingConfiguration = false;
    private boolean skipCheckOnStartup = false;
    private boolean ignoreAnonymousCache = false;
    private Cache2kConfiguration<?,?> defaultManagerConfiguration;

    public Cache2kConfiguration<?, ?> getDefaultManagerConfiguration() {
      return defaultManagerConfiguration;
    }

    public void setDefaultManagerConfiguration(final Cache2kConfiguration<?, ?> _defaultManagerConfiguration) {
      defaultManagerConfiguration = _defaultManagerConfiguration;
    }

    public boolean isIgnoreMissingConfiguration() {
      return ignoreMissingConfiguration;
    }

    public void setIgnoreMissingConfiguration(final boolean _ignoreMissingConfiguration) {
      ignoreMissingConfiguration = _ignoreMissingConfiguration;
    }

    public String getManagerName() {
      return managerName;
    }

    public void setManagerName(final String _managerName) {
      managerName = _managerName;
    }

    public String getVersion() {
      return version;
    }

    public void setVersion(final String _version) {
      version = _version;
    }

    public boolean isSkipCheckOnStartup() {
      return skipCheckOnStartup;
    }

    public void setSkipCheckOnStartup(final boolean _skipCheckOnStartup) {
      skipCheckOnStartup = _skipCheckOnStartup;
    }

    public boolean isIgnoreAnonymousCache() {
      return ignoreAnonymousCache;
    }

    public void setIgnoreAnonymousCache(final boolean _ignoreAnonymousCache) {
      ignoreAnonymousCache = _ignoreAnonymousCache;
    }
  }

}
