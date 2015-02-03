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
package org.apache.hadoop.io.erasurecode;

import java.util.List;

/**
 * A group of blocks or {@link ECBlock} incurred in an erasure coding task for
 * {@link org.apache.hadoop.io.erasurecode.blockcoder.ErasureCoder}.
 * It contains one or more {@link ECSubGroup}s organized according to erasure
 * codec logic.
 *
 * Also see {@link ECSubGroup}.
 */
public abstract class ECGroup extends ECSubGroup {

  /**
   * A constructor specifying data blocks and parity blocks.
   *
   * @param dataBlocks
   * @param parityBlocks
   */
  public ECGroup(ECBlock[] dataBlocks, ECBlock[] parityBlocks) {
    super(dataBlocks, parityBlocks);
  }

  /**
   * Return sub-groups organized by codec specific logic. It's only for erasure
   * coder handling. A codec should know about how to layout, form a group and
   * divide a group into sub-groups, but codec caller won't. In most case there
   * is only one sub-group, itself.
   *
   * @return sub-groups
   */
  public ECSubGroup[] getSubGroups() {
    return new ECSubGroup[] {this};
  }

}
