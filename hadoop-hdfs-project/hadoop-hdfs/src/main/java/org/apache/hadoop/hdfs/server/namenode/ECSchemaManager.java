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
package org.apache.hadoop.hdfs.server.namenode;

import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.erasurecode.ECSchema;
import org.apache.hadoop.io.erasurecode.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The EC Schema Manager handles schemas loading and persisting.
 *
 * This class is instantiated by the FSNamesystem.
 * It maintains the schemas sync between predefined ones and persistent active
 * ones.
 */
@InterfaceAudience.LimitedPrivate({"HDFS"})
public final class ECSchemaManager {
  private static final Logger LOG =
      LoggerFactory.getLogger(ECSchemaManager.class);

  private final Configuration conf;

  private final SchemaLoader schemaLoader;

  /**
   * The FSNamesystem that contains this ECSchemaManager.
   */
  private final FSNamesystem namesystem;

  /**
   * EC schemas, sorted by its name.
   */
  private final Map<String, ECSchema> schemas =
      new TreeMap<String, ECSchema>();

  ECSchemaManager(FSNamesystem namesystem, Configuration conf) {
    this.namesystem = namesystem;
    this.conf = conf;
    this.schemaLoader = new SchemaLoader();
  }


  public void reload() {
    List<ECSchema> predefSchemas = schemaLoader.loadSchema(conf);
    boolean updated = false;

    /**
     * We only accept new predefined schemas for now. In future phase, we may
     * support to update and remove schemas if they're not referenced by zones
     * yet.
     */
    for (ECSchema schema : predefSchemas) {
      if (! schemas.containsKey(schema.getSchemaName())) {
        schemas.put(schema.getSchemaName(), schema);
        updated = true;
      }
    }

    if (updated) {
      // TODO: HDFS-7859 persist into NameNode
    }
  }

  public List<ECSchema> getSchemas() {
    return new ArrayList<>(schemas.values());
  }
}
