// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbFileUnderConstructionEntry extends Table {
  public static FbFileUnderConstructionEntry getRootAsFbFileUnderConstructionEntry(ByteBuffer _bb) { return getRootAsFbFileUnderConstructionEntry(_bb, new FbFileUnderConstructionEntry()); }
  public static FbFileUnderConstructionEntry getRootAsFbFileUnderConstructionEntry(ByteBuffer _bb, FbFileUnderConstructionEntry obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbFileUnderConstructionEntry __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long inodeId() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public String fullPath() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer fullPathAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }

  public static int createFbFileUnderConstructionEntry(FlatBufferBuilder builder,
      long inodeId,
      int fullPath) {
    builder.startObject(2);
    FbFileUnderConstructionEntry.addInodeId(builder, inodeId);
    FbFileUnderConstructionEntry.addFullPath(builder, fullPath);
    return FbFileUnderConstructionEntry.endFbFileUnderConstructionEntry(builder);
  }

  public static void startFbFileUnderConstructionEntry(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addInodeId(FlatBufferBuilder builder, long inodeId) { builder.addLong(0, inodeId, 0); }
  public static void addFullPath(FlatBufferBuilder builder, int fullPathOffset) { builder.addOffset(1, fullPathOffset, 0); }
  public static int endFbFileUnderConstructionEntry(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbFileUnderConstructionEntryBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

