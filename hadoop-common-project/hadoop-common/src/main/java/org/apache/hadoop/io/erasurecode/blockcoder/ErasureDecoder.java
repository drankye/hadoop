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
package org.apache.hadoop.io.erasurecode.blockcoder;


import org.apache.hadoop.io.erasurecode.ECGroup;

import java.util.Iterator;

/**
 * An erasure decoder to perform decoding given a group.
 *
 * It extends {@link ErasureCoder}.
 */
public interface ErasureDecoder extends ErasureCoder {

  /**
   * Perform the decoding given a group. By default it will try the best to
   * attempt to recover all the missing blocks according to the codec logic.
   *
   * @param group
   */
  public CodingStep decode(ECGroup group);

}
