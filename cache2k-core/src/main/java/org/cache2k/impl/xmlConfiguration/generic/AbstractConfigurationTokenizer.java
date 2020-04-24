package org.cache2k.impl.xmlConfiguration.generic;

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

/**
 * @author Jens Wilke
 */
public abstract class AbstractConfigurationTokenizer implements ConfigurationTokenizer {

  private final String source;

  public AbstractConfigurationTokenizer(final String source) {
    this.source = source;
  }

  public String getSource() { return source; }

  protected final Nest returnNest(final String sectionName) {
    return new MyNest(getSource(), getLineNumber(), sectionName);
  }

  protected final Unnest returnUnnest() {
    return new MyUnnest(getSource(), getLineNumber());
  }

  protected final Property returnProperty(final String name, final String property) {
    return new MyProperty(getSource(), getLineNumber(), name, property);
  }

  private static class MyItem implements Item {
    private final String source;
    private final int lineNumber;

    public MyItem(final String source, final int lineNumber) {
      this.lineNumber = lineNumber;
      this.source = source;
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

    public MyNest(final String source, final int lineNumber, final String sectionName) {
      super(source, lineNumber);
      this.sectionName = sectionName;
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
    public MyUnnest(final String source, final int lineNumber) {
      super(source, lineNumber);
    }

    @Override
    public String toString() {
      return "Unnest";
    }
  }

  private static class MyProperty extends MyItem implements Property {
    private final String name;
    private String value;
    private boolean expanded;

    public MyProperty(final String source, final int lineNumber, final String name, final String value) {
      super(source, lineNumber);
      this.name = name;
      this.value = value;
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
    public void setValue(final String v) {
      value = v;
    }

    @Override
    public boolean isExpanded() {
      return expanded;
    }

    @Override
    public void setExpanded(final boolean v) {
      expanded = v;
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
