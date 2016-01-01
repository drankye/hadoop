// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbStringTableSection extends Table {
  public static FbStringTableSection getRootAsFbStringTableSection(ByteBuffer _bb) { return getRootAsFbStringTableSection(_bb, new FbStringTableSection()); }
  public static FbStringTableSection getRootAsFbStringTableSection(ByteBuffer _bb, FbStringTableSection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbStringTableSection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long numEntry() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }

  public static int createFbStringTableSection(FlatBufferBuilder builder,
      long numEntry) {
    builder.startObject(1);
    FbStringTableSection.addNumEntry(builder, numEntry);
    return FbStringTableSection.endFbStringTableSection(builder);
  }

  public static void startFbStringTableSection(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addNumEntry(FlatBufferBuilder builder, long numEntry) { builder.addInt(0, (int)(numEntry & 0xFFFFFFFFL), 0); }
  public static int endFbStringTableSection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbStringTableSectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

