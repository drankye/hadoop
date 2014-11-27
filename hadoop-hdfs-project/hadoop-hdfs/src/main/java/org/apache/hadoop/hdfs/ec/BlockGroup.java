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

import java.util.ArrayList;
import java.util.List;

/**
 * An encoding/decoding work unit that contains necessary information for a
 * erasure codec and coder to perform the work. It can be persisted and stored
 * in NameNode as part of the meta; and transferred to DataNode as part of encoding
 * or decoding command for the corresponding coder to do the work.
 *
 * In simple case like RS codec, a BlockGroup contains only one SubBlockGroup;
 * in LRC codec, there're 3 SubBlockGroups, 2 local group plus 1 global group.
 */
public class BlockGroup {

  private List<SubBlockGroup> subGroups = new ArrayList<SubBlockGroup>();
  private String annotation;

  public List<SubBlockGroup> getSubGroups() {
    return subGroups;
  }

  public void addSubGroup(SubBlockGroup subGroup) {
    subGroups.add(subGroup);
  }
  
  public void setAnnotation(String annotation) {
	  this.annotation = annotation;
  }

  public String getAnnotation() {
	  return annotation;
  }
  
  
}
