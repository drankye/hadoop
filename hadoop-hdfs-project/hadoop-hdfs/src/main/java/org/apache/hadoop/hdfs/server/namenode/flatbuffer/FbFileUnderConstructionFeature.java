// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbFileUnderConstructionFeature extends Table {
  public static FbFileUnderConstructionFeature getRootAsFbFileUnderConstructionFeature(ByteBuffer _bb) { return getRootAsFbFileUnderConstructionFeature(_bb, new FbFileUnderConstructionFeature()); }
  public static FbFileUnderConstructionFeature getRootAsFbFileUnderConstructionFeature(ByteBuffer _bb, FbFileUnderConstructionFeature obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbFileUnderConstructionFeature __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public String clientName() { int o = __offset(4); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer clientNameAsByteBuffer() { return __vector_as_bytebuffer(4, 1); }
  public String clientMachine() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer clientMachineAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }

  public static int createFbFileUnderConstructionFeature(FlatBufferBuilder builder,
      int clientName,
      int clientMachine) {
    builder.startObject(2);
    FbFileUnderConstructionFeature.addClientMachine(builder, clientMachine);
    FbFileUnderConstructionFeature.addClientName(builder, clientName);
    return FbFileUnderConstructionFeature.endFbFileUnderConstructionFeature(builder);
  }

  public static void startFbFileUnderConstructionFeature(FlatBufferBuilder builder) { builder.startObject(2); }
  public static void addClientName(FlatBufferBuilder builder, int clientNameOffset) { builder.addOffset(0, clientNameOffset, 0); }
  public static void addClientMachine(FlatBufferBuilder builder, int clientMachineOffset) { builder.addOffset(1, clientMachineOffset, 0); }
  public static int endFbFileUnderConstructionFeature(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbFileUnderConstructionFeatureBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }

};

