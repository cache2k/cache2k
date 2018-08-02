package org.cache2k.xmlConfiguration;

/*
 * #%L
 * cache2k XML configuration
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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


import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;


/**
 * Test that XML files are valid or not against cache2kv2.xsd
 *
 * @author Jens Wilke
 */
public class XmlValidationTest {
	
	
	
 
  public boolean validateAgainstXSD(InputStream xml) {
	  
	  InputStream xsd = this.getClass().getResourceAsStream("/cache2kv2.xsd");
  
	  try
	    {
	        SchemaFactory factory = 
	            SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
	        Schema schema = factory.newSchema(new StreamSource(xsd));
	        Validator validator = schema.newValidator();
	        validator.validate(new StreamSource(xml));
	        return true;
	    }
	    catch(Exception ex)
	    {
	    	
	        return false;
	    }
	  
  }
  
  @Test
  public void isvalidXmlFile()
  {
	  
	  assertTrue(validateAgainstXSD(this.getClass().getResourceAsStream("/sample7-xsdv21.xml")));
	  assertTrue(validateAgainstXSD(this.getClass().getResourceAsStream("/cache2k-jcacheExample.xml")));
	  assertTrue(validateAgainstXSD(this.getClass().getResourceAsStream("/cache2k-example.xml")));
	  assertTrue(validateAgainstXSD(this.getClass().getResourceAsStream("/cache2k.xml")));
	  
	  assertFalse(validateAgainstXSD(this.getClass().getResourceAsStream("/sample6-xsdv21.xml")));
	  assertFalse(validateAgainstXSD(this.getClass().getResourceAsStream("/cache2k-customizationExample.xml")));
	  
	  assertFalse(validateAgainstXSD(this.getClass().getResourceAsStream("/cache2k-copy.xml")));
	  assertTrue(validateAgainstXSD(this.getClass().getResourceAsStream("/cache2k-customizationExample-Copy.xml")));
	  
  }

 
}
