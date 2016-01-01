// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbXAttrFeatureProto extends Table {
  public static FbXAttrFeatureProto getRootAsFbXAttrFeatureProto(ByteBuffer _bb) { return getRootAsFbXAttrFeatureProto(_bb, new FbXAttrFeatureProto()); }
  public static FbXAttrFeatureProto getRootAsFbXAttrFeatureProto(ByteBuffer _bb, FbXAttrFeatureProto obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbXAttrFeatureProto __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public FbXAttrCompactProto xAttrs(int j) { return xAttrs(new FbXAttrCompactProto(), j); }
  public FbXAttrCompactProto xAttrs(FbXAttrCompactProto obj, int j) { int o = __offset(4); return o != 0 ? obj.__init(__indirect(__vector(o) + j * 4), bb) : null; }
  public int xAttrsLength() { int o = __offset(4); return o != 0 ? __vector_len(o) : 0; }

  public static int createFbXAttrFeatureProto(FlatBufferBuilder builder,
      int xAttrs) {
    builder.startObject(1);
    FbXAttrFeatureProto.addXAttrs(builder, xAttrs);
    return FbXAttrFeatureProto.endFbXAttrFeatureProto(builder);
  }

  public static void startFbXAttrFeatureProto(FlatBufferBuilder builder) { builder.startObject(1); }
  public static void addXAttrs(FlatBufferBuilder builder, int xAttrsOffset) { builder.addOffset(0, xAttrsOffset, 0); }
  public static int createXAttrsVector(FlatBufferBuilder builder, int[] data) { builder.startVector(4, data.length, 4); for (int i = data.length - 1; i >= 0; i--) builder.addOffset(data[i]); return builder.endVector(); }
  public static void startXAttrsVector(FlatBufferBuilder builder, int numElems) { builder.startVector(4, numElems, 4); }
  public static int endFbXAttrFeatureProto(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbXAttrFeatureProtoBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

