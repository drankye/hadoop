// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbSecretManagerSection extends Table {
  public static FbSecretManagerSection getRootAsFbSecretManagerSection(ByteBuffer _bb) { return getRootAsFbSecretManagerSection(_bb, new FbSecretManagerSection()); }
  public static FbSecretManagerSection getRootAsFbSecretManagerSection(ByteBuffer _bb, FbSecretManagerSection obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbSecretManagerSection __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long currentId() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long tokenSequenceNumber() { int o = __offset(6); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long numKeys() { int o = __offset(8); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long numTokens() { int o = __offset(10); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }

  public static int createFbSecretManagerSection(FlatBufferBuilder builder,
      long currentId,
      long tokenSequenceNumber,
      long numKeys,
      long numTokens) {
    builder.startObject(4);
    FbSecretManagerSection.addNumTokens(builder, numTokens);
    FbSecretManagerSection.addNumKeys(builder, numKeys);
    FbSecretManagerSection.addTokenSequenceNumber(builder, tokenSequenceNumber);
    FbSecretManagerSection.addCurrentId(builder, currentId);
    return FbSecretManagerSection.endFbSecretManagerSection(builder);
  }

  public static void startFbSecretManagerSection(FlatBufferBuilder builder) { builder.startObject(4); }
  public static void addCurrentId(FlatBufferBuilder builder, long currentId) { builder.addInt(0, (int)(currentId & 0xFFFFFFFFL), 0); }
  public static void addTokenSequenceNumber(FlatBufferBuilder builder, long tokenSequenceNumber) { builder.addInt(1, (int)(tokenSequenceNumber & 0xFFFFFFFFL), 0); }
  public static void addNumKeys(FlatBufferBuilder builder, long numKeys) { builder.addInt(2, (int)(numKeys & 0xFFFFFFFFL), 0); }
  public static void addNumTokens(FlatBufferBuilder builder, long numTokens) { builder.addInt(3, (int)(numTokens & 0xFFFFFFFFL), 0); }
  public static int endFbSecretManagerSection(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishFbSecretManagerSectionBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

