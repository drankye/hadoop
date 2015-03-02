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
package org.apache.hadoop.io.erasurecode;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CommonConfigurationKeys;
import org.apache.hadoop.util.ReflectionUtils;

import java.util.*;

/**
 * A facility that manages all erasure codecs, and finds the right codec given
 * its name.
 */
public class ErasureCodecLoader {

  public static final Log LOG =
      LogFactory.getLog(ErasureCodecLoader.class.getName());

  private static final ServiceLoader<ErasureCodec> CODEC_PROVIDERS =
      ServiceLoader.load(ErasureCodec.class);

  /**
   * A map from the codec name to the codec.
   */
  private Map<String, ErasureCodec> codecs = null;

  /**
   * Print the codecs map out as a string.
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    Iterator<Map.Entry<String, ErasureCodec>> itr =
        codecs.entrySet().iterator();
    buf.append("{ ");
    if (itr.hasNext()) {
      Map.Entry<String, ErasureCodec> entry = itr.next();
      buf.append(entry.getKey());
      buf.append(": ");
      buf.append(entry.getValue().getClass().getName());
      while (itr.hasNext()) {
        entry = itr.next();
        buf.append(", ");
        buf.append(entry.getKey());
        buf.append(": ");
        buf.append(entry.getValue().getClass().getName());
      }
    }
    buf.append(" }");
    return buf.toString();
  }

  /**
   * Get the list of codecs discovered via a Java ServiceLoader, and/or
   * listed in the configuration. Codecs specified in configuration come
   * later in the returned list, and are considered to override those
   * from the ServiceLoader.
   * @param conf the configuration to look at
   * @return a list of {@link org.apache.hadoop.io.erasurecode.ErasureCodec}
   */
  public static List<Class<? extends ErasureCodec>> load(Configuration conf) {
    List<Class<? extends ErasureCodec>> results
        = new ArrayList<Class<? extends ErasureCodec>>();
    // Add codec classes discovered via service loading
    synchronized (CODEC_PROVIDERS) {
      // CODEC_PROVIDERS is a lazy collection. Synchronize so it is
      // thread-safe. See HADOOP-8406.
      for (ErasureCodec codec : CODEC_PROVIDERS) {
        results.add(codec.getClass());
      }
    }
    // Add codec classes from configuration
    String codecsString = conf.get(
        CommonConfigurationKeys.IO_ERASURE_CODECS_KEY);
    if (codecsString != null) {
      StringTokenizer codecSplit = new StringTokenizer(codecsString, ",");
      while (codecSplit.hasMoreElements()) {
        String codecSubstring = codecSplit.nextToken().trim();
        if (codecSubstring.length() != 0) {
          try {
            Class<?> cls = conf.getClassByName(codecSubstring);
            if (!ErasureCodec.class.isAssignableFrom(cls)) {
              throw new IllegalArgumentException("Class " + codecSubstring +
                  " is not a ErasureCodec");
            }
            results.add(cls.asSubclass(ErasureCodec.class));
          } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Erasure codec " +
                codecSubstring + " not found.",
                ex);
          }
        }
      }
    }

    return results;
  }

  /**
   * Sets a list of codec classes in the configuration. In addition to any
   * classes specified using this method,
   * {@link org.apache.hadoop.io.erasurecode.ErasureCodec} classes on
   * the classpath are discovered using a Java ServiceLoader.
   * @param conf the configuration to modify
   * @param classes the list of classes to set
   */
  public static void setCodecs(Configuration conf,
                                     List<Class> classes) {
    StringBuilder buf = new StringBuilder();
    Iterator<Class> itr = classes.iterator();
    if (itr.hasNext()) {
      Class cls = itr.next();
      buf.append(cls.getName());
      while(itr.hasNext()) {
        buf.append(',');
        buf.append(itr.next().getName());
      }
    }
    conf.set(CommonConfigurationKeys.IO_ERASURE_CODECS_KEY, buf.toString());
  }

  /**
   * Find the codecs specified in the config value io.compression.codecs
   * and register them. Defaults to gzip and deflate.
   */
  public ErasureCodecLoader(Configuration conf) {
    codecs = new HashMap<String, ErasureCodec>();
    List<Class<? extends ErasureCodec>> codecClasses = load(conf);
    if (codecClasses == null || codecClasses.isEmpty()) {
      // addCodec(new RSErasureCodec());
    } else {
      for (Class<? extends ErasureCodec> codecClass : codecClasses) {
        addCodec(ReflectionUtils.newInstance(codecClass, conf));
      }
    }
  }

  /**
   * Find the erasure codec given the codec name.
   *
   * @param codecName the codec name
   * @return the codec object
   */
  public ErasureCodec getCodecByName(String codecName) {
    ErasureCodec codec = codecs.get(codecName);
    return codec;
  }

  private void addCodec(ErasureCodec codec) {
    String codecName = getCodecName(codec);
    codecs.put(codecName, codec);
  }

  /**
   * Make codec name according to the codec class.
   *
   * Codec names are case insensitive.
   *
   * The code name is the short class name (without the package name).
   * If the short class name ends with 'ErasureCodec', then "ErasureCodec" will
   * be removed. For example for the 'RSErasureCodec' codec the codec name will
   * be 'rs'.
   *
   * @param codecClass
   * @return codec name
   */
  public static String getCodecName(Class<? extends ErasureCodec> codecClass) {
    String codecName = codecClass.getSimpleName();
    if (codecName.endsWith("ErasureCodec")) {
      codecName = codecName.substring(0, codecName.length() -
          "ErasureCodec".length());
    }
    return codecName.toLowerCase();
  }

  public static String getCodecName(ErasureCodec codec) {
    return getCodecName(codec.getClass());
  }
}
