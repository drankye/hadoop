// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbCacheManagerSection extends Table {
  public static FbCacheManagerSection getRootAsFbCacheManagerSection(ByteBuffer _bb) { return getRootAsFbCacheManagerSection(_bb, new FbCacheManagerSection()); }
  public static FbCacheManagerSection getRootAsFbCacheManagerSection(ByteBuffer _bb, FbCacheManagerSection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbCacheManagerSection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long nextDirectiveId() { int o = __offset(4); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long numPools() { int o = __offset(6); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long numDirectives() { int o = __offset(8); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }

  public static int createFbCacheManagerSection(FlatBufferBuilder builder,
      long nextDirectiveId,
      long numPools,
      long numDirectives) {
    builder.startObject(3);
    FbCacheManagerSection.addNextDirectiveId(builder, nextDirectiveId);
    FbCacheManagerSection.addNumDirectives(builder, numDirectives);
    FbCacheManagerSection.addNumPools(builder, numPools);
    return FbCacheManagerSection.endFbCacheManagerSection(builder);
  }

  public static void startFbCacheManagerSection(FlatBufferBuilder builder) { builder.startObject(3); }
  public static void addNextDirectiveId(FlatBufferBuilder builder, long nextDirectiveId) { builder.addLong(0, nextDirectiveId, 0); }
  public static void addNumPools(FlatBufferBuilder builder, long numPools) { builder.addInt(1, (int)(numPools & 0xFFFFFFFFL), 0); }
  public static void addNumDirectives(FlatBufferBuilder builder, long numDirectives) { builder.addInt(2, (int)(numDirectives & 0xFFFFFFFFL), 0); }
  public static int endFbCacheManagerSection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbCacheManagerSectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

