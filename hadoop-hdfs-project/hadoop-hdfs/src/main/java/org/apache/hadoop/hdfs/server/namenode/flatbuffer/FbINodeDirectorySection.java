// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbINodeDirectorySection extends Table {
  public static FbINodeDirectorySection getRootAsFbINodeDirectorySection(ByteBuffer _bb) { return getRootAsFbINodeDirectorySection(_bb, new FbINodeDirectorySection()); }
  public static FbINodeDirectorySection getRootAsFbINodeDirectorySection(ByteBuffer _bb, FbINodeDirectorySection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbINodeDirectorySection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }


  public static void startFbINodeDirectorySection(FlatBufferBuilder builder) { builder.startObject(0); }
  public static int endFbINodeDirectorySection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbINodeDirectorySectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

