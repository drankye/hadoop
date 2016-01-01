// automatically generated, do not modify
package org.apache.hadoop.hdfs.server.namenode.flatbuffer;
import java.nio.*;
import java.lang.*;

import com.google.flatbuffers.*;

@SuppressWarnings("unused")
public final class FbPersistToken extends Table {
  public static FbPersistToken getRootAsIntelPersistToken(ByteBuffer _bb) { return getRootAsIntelPersistToken(_bb, new FbPersistToken()); }
  public static FbPersistToken getRootAsIntelPersistToken(ByteBuffer _bb, FbPersistToken obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__init(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public FbPersistToken __init(int _i, ByteBuffer _bb) { bb_pos = _i; bb = _bb; return this; }

  public long version() { int o = __offset(4); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public String owner() { int o = __offset(6); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer ownerAsByteBuffer() { return __vector_as_bytebuffer(6, 1); }
  public String renewer() { int o = __offset(8); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer renewerAsByteBuffer() { return __vector_as_bytebuffer(8, 1); }
  public String realUser() { int o = __offset(10); return o != 0 ? __string(o + bb_pos) : null; }
  public ByteBuffer realUserAsByteBuffer() { return __vector_as_bytebuffer(10, 1); }
  public long issueDate() { int o = __offset(12); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long maxDate() { int o = __offset(14); return o != 0 ? bb.getLong(o + bb_pos) : 0; }
  public long sequenceNumber() { int o = __offset(16); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long masterKeyId() { int o = __offset(18); return o != 0 ? (long)bb.getInt(o + bb_pos) & 0xFFFFFFFFL : 0; }
  public long expiryDate() { int o = __offset(20); return o != 0 ? bb.getLong(o + bb_pos) : 0; }

  public static int createIntelPersistToken(FlatBufferBuilder builder,
      long version,
      int owner,
      int renewer,
      int realUser,
      long issueDate,
      long maxDate,
      long sequenceNumber,
      long masterKeyId,
      long expiryDate) {
    builder.startObject(9);
    FbPersistToken.addExpiryDate(builder, expiryDate);
    FbPersistToken.addMaxDate(builder, maxDate);
    FbPersistToken.addIssueDate(builder, issueDate);
    FbPersistToken.addMasterKeyId(builder, masterKeyId);
    FbPersistToken.addSequenceNumber(builder, sequenceNumber);
    FbPersistToken.addRealUser(builder, realUser);
    FbPersistToken.addRenewer(builder, renewer);
    FbPersistToken.addOwner(builder, owner);
    FbPersistToken.addVersion(builder, version);
    return FbPersistToken.endIntelPersistToken(builder);
  }

  public static void startIntelPersistToken(FlatBufferBuilder builder) { builder.startObject(9); }
  public static void addVersion(FlatBufferBuilder builder, long version) { builder.addInt(0, (int)(version & 0xFFFFFFFFL), 0); }
  public static void addOwner(FlatBufferBuilder builder, int ownerOffset) { builder.addOffset(1, ownerOffset, 0); }
  public static void addRenewer(FlatBufferBuilder builder, int renewerOffset) { builder.addOffset(2, renewerOffset, 0); }
  public static void addRealUser(FlatBufferBuilder builder, int realUserOffset) { builder.addOffset(3, realUserOffset, 0); }
  public static void addIssueDate(FlatBufferBuilder builder, long issueDate) { builder.addLong(4, issueDate, 0); }
  public static void addMaxDate(FlatBufferBuilder builder, long maxDate) { builder.addLong(5, maxDate, 0); }
  public static void addSequenceNumber(FlatBufferBuilder builder, long sequenceNumber) { builder.addInt(6, (int)(sequenceNumber & 0xFFFFFFFFL), 0); }
  public static void addMasterKeyId(FlatBufferBuilder builder, long masterKeyId) { builder.addInt(7, (int)(masterKeyId & 0xFFFFFFFFL), 0); }
  public static void addExpiryDate(FlatBufferBuilder builder, long expiryDate) { builder.addLong(8, expiryDate, 0); }
  public static int endIntelPersistToken(FlatBufferBuilder builder) {
    int o = builder.endObject();
    return o;
  }
  public static void finishIntelPersistTokenBuffer(FlatBufferBuilder builder, int offset) { builder.finish(offset); }
};

