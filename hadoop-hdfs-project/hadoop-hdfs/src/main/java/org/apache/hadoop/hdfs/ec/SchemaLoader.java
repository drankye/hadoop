/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hdfs.ec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaLoader {
  public static final Log LOG = LogFactory.getLog(SchemaLoader.class.getName());

  private static final String CODEC_CONF_PREFIX = "hadoop.hdfs.ec.erasurecodec.codec.";

  /**
   * Load ec schemas from the config file. This file is
   * expected to be in the XML format specified in the design doc.
   * @return codecs form configure file
   * @throws IOException
   *             if the config file cannot be read.
   * @throws ParserConfigurationException
   *             if XML parser is misconfigured.
   * @throws SAXException
   *             if config file is malformed.
   */
  public synchronized List<ECSchema> loadSchema(Configuration conf) throws IOException,
      ParserConfigurationException, SAXException {
    File confFile = getConfigurationFile(conf);
    if (confFile == null) {
      return new ArrayList<ECSchema>();
    }
    LOG.info("Loading EC schema file " + confFile);

    // Read and parse the schema file.
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    docBuilderFactory.setIgnoringComments(true);
    DocumentBuilder builder = docBuilderFactory.newDocumentBuilder();
    Document doc = builder.parse(confFile);
    Element root = doc.getDocumentElement();
    if (!"schemas".equals(root.getTagName()))
      throw new RuntimeException(
          "Bad EC schema config file: top-level element not <schemas>");
    NodeList elements = root.getChildNodes();
    List<ECSchema> codecs = new ArrayList<ECSchema>();
    for (int i = 0; i < elements.getLength(); i++) {
      Node node = elements.item(i);
      if (node instanceof Element) {
        Element element = (Element) node;
        if ("schema".equals(element.getTagName())) {
          ECSchema codec = loadSchema(element);
          String codecClassName = conf.get(CODEC_CONF_PREFIX + codec.getCodecName());
          if (codecClassName != null) {
        	  codec.setSchemaClassName(codecClassName);
        	  codecs.add(codec);
          }
        } else {
          LOG.warn("Bad element in EC schema configuration file: " + element.getTagName());
        }
      }
    }
    return codecs;
  }

  /**
   * Path to XML file containing schemas. If the path is relative, it is
   * searched for in the classpath, but loaded like a regular File.
   */
  private File getConfigurationFile(Configuration conf) {
    String allocFilePath = conf.get(ECConfiguration.CONFIGURATION_FILE,
        ECConfiguration.DEFAULT_CONFIGURATION_FILE);
    File allocFile = new File(allocFilePath);
    if (!allocFile.isAbsolute()) {
      URL url = Thread.currentThread().getContextClassLoader()
          .getResource(allocFilePath);
      if (url == null) {
        LOG.warn(allocFilePath + " not found on the classpath.");
        allocFile = null;
      } else if (!url.getProtocol().equalsIgnoreCase("file")) {
        throw new RuntimeException(
            "EC configuration file "
                + url
                + " found on the classpath is not on the local filesystem.");
      } else {
        allocFile = new File(url.getPath());
      }
    }
    return allocFile;
  }

  /**
   * Loads a schema from a schema element in the configuration file
   */
  private ECSchema loadSchema(Element element) {
    String schemaName = element.getAttribute("name");
    Map<String, String> ecProperties = new HashMap<String, String>();
    String erasureCoder = null;
    NodeList fields = element.getChildNodes();

    for (int j = 0; j < fields.getLength(); j++) {
      Node fieldNode = fields.item(j);
      if (!(fieldNode instanceof Element))
        continue;
      Element field = (Element) fieldNode;
      String tagName = field.getTagName();
      if ("codec".equals(tagName)) {
        erasureCoder = ((Text)field.getFirstChild()).getData().trim();
        ecProperties.put(tagName, erasureCoder);
      } else {
        String value = ((Text)field.getFirstChild()).getData().trim();
        ecProperties.put(tagName, value);
      }
    }

    ECSchema codec = new ECSchema(schemaName, ecProperties, erasureCoder);
    return codec;
  }
}
