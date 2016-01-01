// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbINodeSection extends Table {
  public static FbINodeSection getRootAsFbINodeSection(ByteBuffer _bb) { return getRootAsFbINodeSection(_bb, new FbINodeSection()); }
  public static FbINodeSection getRootAsFbINodeSection(ByteBuffer _bb, FbINodeSection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbINodeSection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long lastInodeId() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long numInodes() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }

  public static int createFbINodeSection(FlatBufferBuilder builder,
      long lastInodeId,
      long numInodes) {
    builder.startObject(2);
    FbINodeSection.addNumInodes(builder, numInodes);
    FbINodeSection.addLastInodeId(builder, lastInodeId);
    return FbINodeSection.endFbINodeSection(builder);
  }

  public static void startFbINodeSection(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addLastInodeId(FlatBufferBuilder builder, long lastInodeId) { builder.addLong(0, lastInodeId, 0); }
  public static void addNumInodes(FlatBufferBuilder builder, long numInodes) { builder.addLong(1, numInodes, 0); }
  public static int endFbINodeSection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbINodeSectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

