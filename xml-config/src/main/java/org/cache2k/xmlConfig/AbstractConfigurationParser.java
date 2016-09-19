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

/**
 * @author Jens Wilke
 */
public abstract class AbstractConfigurationParser implements ConfigurationParser {

  private final String source;

  public AbstractConfigurationParser(final String _source) {
    source = _source;
  }

  public String getSource() { return source; }

  protected final Nest returnNest(final String _secionName) {
    return new MyNest(getSource(), getLineNumber(), _secionName);
  }

  protected final Unnest returnUnnest() {
    return new MyUnnest(getSource(), getLineNumber());
  }

  protected final Property returnProperty(final String _name, final String _property) {
    return new MyProperty(getSource(), getLineNumber(), _name, _property);
  }

  private static class MyItem implements Item {
    private final String source;
    private final int lineNumber;

    public MyItem(final String _source, final int _lineNumber) {
      lineNumber = _lineNumber;
      source = _source;
    }

    @Override
    public String getSource() {
      return source;
    }

    @Override
    public int getLineNumber() {
      return lineNumber;
    }
  }

  private static class MyNest extends MyItem implements Nest {

    private final String sectionName;

    public MyNest(final String _source, final int _lineNumber, final String _sectionName) {
      super(_source, _lineNumber);
      sectionName = _sectionName;
    }

    @Override
    public String getSectionName() {
      return sectionName;
    }

    @Override
    public String toString() {
      return "Nest{" +
        "sectionName='" + sectionName + '\'' +
        '}';
    }
  }

  private static class MyUnnest extends MyItem implements Unnest {
    public MyUnnest(final String _source, final int _lineNumber) {
      super(_source, _lineNumber);
    }

    @Override
    public String toString() {
      return "Unnest";
    }
  }

  private static class MyProperty extends MyItem implements Property {
    private final String name;
    private final String value;

    public MyProperty(final String _source, final int _lineNumber, final String _name, final String _value) {
      super(_source, _lineNumber);
      name = _name;
      value = _value;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "Property{" +
        "name='" + name + '\'' +
        ", value='" + value + '\'' +
        '}';
    }
  }

}
