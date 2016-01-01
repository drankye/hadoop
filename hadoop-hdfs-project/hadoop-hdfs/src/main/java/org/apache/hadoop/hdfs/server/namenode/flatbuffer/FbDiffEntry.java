// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbDiffEntry extends Table {
  public static FbDiffEntry getRootAsFbDiffEntry(ByteBuffer _bb) { return getRootAsFbDiffEntry(_bb, new FbDiffEntry()); }
  public static FbDiffEntry getRootAsFbDiffEntry(ByteBuffer _bb, FbDiffEntry obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbDiffEntry __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public int type() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }
  public long inodeId() { int o = __offset(6); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long numOfDiff() { int o = __offset(8); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }

  public static int createFbDiffEntry(FlatBufferBuilder builder,
      int type,
      long inodeId,
      long numOfDiff) {
    builder.startObject(3);
    FbDiffEntry.addInodeId(builder, inodeId);
    FbDiffEntry.addNumOfDiff(builder, numOfDiff);
    FbDiffEntry.addType(builder, type);
    return FbDiffEntry.endFbDiffEntry(builder);
  }

  public static void startFbDiffEntry(FlatBufferBuilder builder) { builder.startObject(3); }
  public static void addType(FlatBufferBuilder builder, int type) { builder.addInt(0, type, 0); }
  public static void addInodeId(FlatBufferBuilder builder, long inodeId) { builder.addLong(1, inodeId, 0); }
  public static void addNumOfDiff(FlatBufferBuilder builder, long numOfDiff) { builder.addInt(2, (int)(numOfDiff & 0xFFFFFFFFL), 0); }
  public static int endFbDiffEntry(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbDiffEntryBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

