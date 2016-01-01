// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbINodeReferenceSection extends Table {
  public static FbINodeReferenceSection getRootAsFbINodeReferenceSection(ByteBuffer _bb) { return getRootAsFbINodeReferenceSection(_bb, new FbINodeReferenceSection()); }
  public static FbINodeReferenceSection getRootAsFbINodeReferenceSection(ByteBuffer _bb, FbINodeReferenceSection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbINodeReferenceSection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }


  public static void startFbINodeReferenceSection(FlatBufferBuilder builder) { builder.startObject(0); }
  public static int endFbINodeReferenceSection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbINodeReferenceSectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

