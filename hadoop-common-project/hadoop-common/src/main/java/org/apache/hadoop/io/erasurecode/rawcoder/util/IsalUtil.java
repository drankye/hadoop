/*
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

package org.apache.hadoop.io.erasurecode.rawcoder.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.util.NativeCodeLoader;

/**
 * Intel ISA-L related utilities.
 */
public class IsalUtil {

    private static final Log LOG =
            LogFactory.getLog(IsalUtil.class.getName());

    private static boolean nativeIsalLoaded = false;

    static {
        if (NativeCodeLoader.isNativeCodeLoaded() &&
                NativeCodeLoader.buildSupportsIsal()) {
            try {
                loadLibrary();
                nativeIsalLoaded = true;
            } catch (Throwable t) {
                LOG.error("failed to load ISA-L", t);
            }
        }
    }

    public static boolean isNativeCodeLoaded() {
        return nativeIsalLoaded;
    }

    /**
   * Is the native ISA-L library loaded & initialized?
   */
  public static void checkNativeCodeLoaded() {
      if (!nativeIsalLoaded) {
        throw new RuntimeException("Native ISA-L library not available: " +
            "this version of libhadoop was built without " +
            "ISA-L support.");
      }
  }

  public static native void loadLibrary();

  public static native String getLibraryName();
}
