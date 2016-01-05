/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdfs.server.namenode;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos.CacheDirectiveInfoProto;
import org.apache.hadoop.hdfs.protocol.proto.ClientNamenodeProtocolProtos.CachePoolInfoProto;
import org.apache.hadoop.hdfs.security.token.delegation.DelegationTokenSecretManager;
import org.apache.hadoop.hdfs.server.blockmanagement.BlockIdManager;
import org.apache.hadoop.hdfs.server.common.HdfsServerConstants;
import org.apache.hadoop.hdfs.server.namenode.flatbuffer.*;
import org.apache.hadoop.hdfs.server.namenode.FsImageProto.CacheManagerSection;
import org.apache.hadoop.hdfs.server.namenode.FsImageProto.FileSummary;
import org.apache.hadoop.hdfs.server.namenode.FsImageProto.NameSystemSection;
import org.apache.hadoop.hdfs.server.namenode.FsImageProto.SecretManagerSection;
import org.apache.hadoop.hdfs.server.namenode.FsImageProto.StringTableSection;
import org.apache.hadoop.hdfs.server.namenode.snapshot.FSImageFormatPBSnapshot;
import org.apache.hadoop.hdfs.server.namenode.startupprogress.Phase;
import org.apache.hadoop.hdfs.server.namenode.startupprogress.StartupProgress;
import org.apache.hadoop.hdfs.server.namenode.startupprogress.StartupProgress.Counter;
import org.apache.hadoop.hdfs.server.namenode.startupprogress.Step;
import org.apache.hadoop.hdfs.server.namenode.startupprogress.StepType;
import org.apache.hadoop.hdfs.util.MD5FileUtils;
import org.apache.hadoop.io.MD5Hash;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressorStream;
import org.apache.hadoop.util.LimitInputStream;
import org.apache.hadoop.util.Time;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.CodedOutputStream;

/**
 * Utility class to read / write fsimage in protobuf format.
 */
@InterfaceAudience.Private
public final class FSImageFormatProtobuf {
  private static final Log LOG = LogFactory.getLog(FSImageFormatProtobuf.class);

  public static final class LoaderContext {
    private String[] stringTable;
    private final ArrayList<INodeReference> refList = Lists.newArrayList();

    public String[] getStringTable() {
      return stringTable;
    }

    public ArrayList<INodeReference> getRefList() {
      return refList;
    }
  }

  public static final class SaverContext {
    public static class DeduplicationMap<E> {
      private final Map<E, Integer> map = Maps.newHashMap();
      private DeduplicationMap() {}

      static <T> DeduplicationMap<T> newMap() {
        return new DeduplicationMap<T>();
      }

      int getId(E value) {
        if (value == null) {
          return 0;
        }
        Integer v = map.get(value);
        if (v == null) {
          int nv = map.size() + 1;
          map.put(value, nv);
          return nv;
        }
        return v;
      }

      int size() {
        return map.size();
      }

      Set<Entry<E, Integer>> entrySet() {
        return map.entrySet();
      }
    }
    private final ArrayList<INodeReference> refList = Lists.newArrayList();

    private final DeduplicationMap<String> stringMap = DeduplicationMap
        .newMap();

    public DeduplicationMap<String> getStringMap() {
      return stringMap;
    }

    public ArrayList<INodeReference> getRefList() {
      return refList;
    }
  }

  public static final class Loader implements FSImageFormat.AbstractLoader {
    static final int MINIMUM_FILE_LENGTH = 8;
    private final Configuration conf;
    private final FSNamesystem fsn;
    private final LoaderContext ctx;
    /** The MD5 sum of the loaded file */
    private MD5Hash imgDigest;
    /** The transaction ID of the last edit represented by the loaded file */
    private long imgTxId;
    /**
     * Whether the image's layout version must be the same with
     * {@link HdfsServerConstants#NAMENODE_LAYOUT_VERSION}. This is only set to true
     * when we're doing (rollingUpgrade rollback).
     */
    private final boolean requireSameLayoutVersion;

    Loader(Configuration conf, FSNamesystem fsn,
        boolean requireSameLayoutVersion) {
      this.conf = conf;
      this.fsn = fsn;
      this.ctx = new LoaderContext();
      this.requireSameLayoutVersion = requireSameLayoutVersion;
    }

    @Override
    public MD5Hash getLoadedImageMd5() {
      return imgDigest;
    }

    @Override
    public long getLoadedImageTxId() {
      return imgTxId;
    }

    public LoaderContext getLoaderContext() {
      return ctx;
    }

    void load(File file) throws IOException {
      imgDigest = MD5FileUtils.computeMd5ForFile(file);
      RandomAccessFile raFile = new RandomAccessFile(file, "r");
      try {
        loadFbInternal(raFile);
      } finally {
        raFile.close();
      }
    }

    private void loadFbInternal(RandomAccessFile raFile)
      throws IOException{
      if (!FSImageUtil.checkFileFormat(raFile)) {
        throw new IOException("Unrecongnized file format");
      }

      FbFileSummary summary = FSImageUtil.loadFbSummary(raFile);
      if (requireSameLayoutVersion &&
          summary.layoutVersion() != HdfsServerConstants.NAMENODE_LAYOUT_VERSION) {
        throw new IOException("Image version " + summary.layoutVersion() +
            "is not equal to the software version " +
            HdfsServerConstants.NAMENODE_LAYOUT_VERSION);
      }
      FileChannel channel = raFile.getChannel();
      FSImageFormatPBINode.Loader inodeLoader = new FSImageFormatPBINode.Loader(
          fsn, this);
      FSImageFormatPBSnapshot.Loader snapshotLoader = new FSImageFormatPBSnapshot.Loader(
          fsn, this);

      List<FbSection> sections = new ArrayList<>();
      for (int i = 0; i < summary.sectionsLength();i++) {
        sections.add(summary.sections(i));
      }
      Collections.sort(sections, new Comparator<FbSection>() {
        @Override
        public int compare(FbSection s1, FbSection s2) {
          SectionName n1 = SectionName.fromString(s1.name());
          SectionName n2 = SectionName.fromString(s2.name());
          if (n1 == null) {
            return n2 == null ? 0 : -1;
          } else if (n2 == null) {
            return -1;
          } else {
            return n1.ordinal() - n2.ordinal();
          }
        }
      });
      StartupProgress prog = NameNode.getStartupProgress();
      Step currentStep = null;
      for (FbSection s:sections) {
        ByteBuffer in = channel.map(FileChannel.MapMode.READ_ONLY,
            s.offset(), s.length());
        //channel.position(s.offset());
        //in = FSImageUtil.wrapInputStreamForCompression(conf,
        //    summary.codec(), in);

        String n = s.name();

        switch (SectionName.fromString(n)) {
          case NS_INFO:
            loadFbNameSystemSection(in);   // success
            break;
          case STRING_TABLE:
            loadFbStringTableSection(in); // success
            break;
          case INODE: {
            currentStep = new Step(StepType.INODES);
            prog.beginStep(Phase.LOADING_FSIMAGE, currentStep);
              inodeLoader.loadFbINodeSection(in, prog, currentStep); // success
          }
          break;
          case INODE_REFERENCE:
            snapshotLoader.loadFbINodeReferenceSection(in); // success
            break;
          case INODE_DIR:
            inodeLoader.loadFbINodeDirectorySection(in); // success
            break;
          case FILES_UNDERCONSTRUCTION:
            inodeLoader.loadFbFilesUnderConstructionSection(in); // success
            break;
          case SNAPSHOT:
            //snapshotLoader.loadFbSnapshotSection(in); // success
            break;
          case SNAPSHOT_DIFF:
            //snapshotLoader.loadFbSnapshotDiffSection(in); // succcess
            break;
          case SECRET_MANAGER: {
            prog.endStep(Phase.LOADING_FSIMAGE, currentStep);
            Step step = new Step(StepType.DELEGATION_TOKENS);
            prog.beginStep(Phase.LOADING_FSIMAGE, step);
            loadFbSecretManagerSection(in, prog, step);  // success
            prog.endStep(Phase.LOADING_FSIMAGE, step);
          }
          break;
          case CACHE_MANAGER: {
            Step step = new Step(StepType.CACHE_POOLS);
            prog.beginStep(Phase.LOADING_FSIMAGE, step);
            //loadFbCacheManagerSection(in, prog, step); // success
            prog.endStep(Phase.LOADING_FSIMAGE, step);
          }
          break;
          default:
            LOG.warn("Unrecognized section " + n);
            break;
        }
      }
    }

    public static ByteBuffer parseFrom(ByteBuffer in) throws IOException {
      int len = in.getInt();
      return in;
    }

    private void loadFbNameSystemSection(ByteBuffer in) throws IOException {
      ByteBuffer byteBuffer = parseFrom(in);
      FbNameSystemSection fbNameSystemSection =
          FbNameSystemSection.getRootAsFbNameSystemSection(byteBuffer);
      BlockIdManager blockIdManager = fsn.getBlockIdManager();
      blockIdManager.setGenerationStampV1(fbNameSystemSection.genstampV1());
      blockIdManager.setGenerationStampV2(fbNameSystemSection.genstampV2());
      blockIdManager.setGenerationStampV1Limit(fbNameSystemSection.genstampV1Limit());
      blockIdManager.setLastAllocatedBlockId(fbNameSystemSection.lastAllocatedBlockId());
      imgTxId = fbNameSystemSection.transactionId();
//      long roll = fbNameSystemSection.rollingUpgradeStartTime();
//      boolean hasRoll = roll != 0;
      if (fbNameSystemSection.rollingUpgradeStartTime() != 0
          && fsn.getFSImage().hasRollbackFSImage()) {
        // we set the rollingUpgradeInfo only when we make sure we have the
        // rollback image
        fsn.setRollingUpgradeInfo(true, fbNameSystemSection.rollingUpgradeStartTime());
      }
    }

    private void loadNameSystemSection(InputStream in) throws IOException {
      NameSystemSection s = NameSystemSection.parseDelimitedFrom(in);
      BlockIdManager blockIdManager = fsn.getBlockIdManager();
      blockIdManager.setGenerationStampV1(s.getGenstampV1());
      blockIdManager.setGenerationStampV2(s.getGenstampV2());
      blockIdManager.setGenerationStampV1Limit(s.getGenstampV1Limit());
      blockIdManager.setLastAllocatedBlockId(s.getLastAllocatedBlockId());
      imgTxId = s.getTransactionId();
      if (s.hasRollingUpgradeStartTime()
          && fsn.getFSImage().hasRollbackFSImage()) {
        // we set the rollingUpgradeInfo only when we make sure we have the
        // rollback image
        fsn.setRollingUpgradeInfo(true, s.getRollingUpgradeStartTime());
      }
    }

    private void loadFbStringTableSection(ByteBuffer in) throws IOException {
      parseFrom(in);
      FbStringTableSection fbSts =
          FbStringTableSection.getRootAsFbStringTableSection(in);
      ctx.stringTable = new String[(int)fbSts.numEntry() + 1];
      for (int i = 0; i < fbSts.numEntry(); ++i) {
        parseFrom(in);
        FbEntry fbEntry = FbEntry.getRootAsFbEntry(in);
        ctx.stringTable[(int) fbEntry.id()] = fbEntry.str();
      }
    }

    private void loadStringTableSection(InputStream in) throws IOException {
      StringTableSection s = StringTableSection.parseDelimitedFrom(in);
      ctx.stringTable = new String[s.getNumEntry() + 1];
      for (int i = 0; i < s.getNumEntry(); ++i) {
        StringTableSection.Entry e = StringTableSection.Entry
            .parseDelimitedFrom(in);
        ctx.stringTable[e.getId()] = e.getStr();
      }
    }

    private void loadFbSecretManagerSection(ByteBuffer in, StartupProgress prog,
                                          Step currentStep) throws IOException {
      ByteBuffer byteBuffer = parseFrom(in);
      FbSecretManagerSection is = FbSecretManagerSection.
          getRootAsFbSecretManagerSection(byteBuffer);

      long numKeys = is.numKeys(), numTokens = is.numTokens();
      ArrayList<FbDelegationKey> fbkeys = Lists
          .newArrayListWithCapacity((int)numKeys);
      ArrayList<FbPersistToken> fbtokens = Lists
          .newArrayListWithCapacity((int)numTokens);

      for (int i = 0; i < numKeys; ++i) {
        ByteBuffer byteBuffer1 = parseFrom(in);
        FbDelegationKey fbDelegationKey = FbDelegationKey.getRootAsFbDelegationKey(byteBuffer1);
        fbkeys.add(fbDelegationKey);
      }

      prog.setTotal(Phase.LOADING_FSIMAGE, currentStep, numTokens);
      Counter counter = prog.getCounter(Phase.LOADING_FSIMAGE, currentStep);
      for (int i = 0; i < numTokens; ++i) {
        ByteBuffer byteBuffer2 = parseFrom(in);
        FbPersistToken fbPersistToken = FbPersistToken.getRootAsFbPersistToken(byteBuffer2);
        fbtokens.add(fbPersistToken);
        counter.increment();
      }

      fsn.loadSecretManagerState(null, is, null, fbkeys, fbtokens, null);
    }

    private void loadSecretManagerSection(InputStream in, StartupProgress prog,
        Step currentStep) throws IOException {
      SecretManagerSection s = SecretManagerSection.parseDelimitedFrom(in);
      int numKeys = s.getNumKeys(), numTokens = s.getNumTokens();
      ArrayList<SecretManagerSection.DelegationKey> keys = Lists
          .newArrayListWithCapacity(numKeys);
      ArrayList<SecretManagerSection.PersistToken> tokens = Lists
          .newArrayListWithCapacity(numTokens);

      for (int i = 0; i < numKeys; ++i)
        keys.add(SecretManagerSection.DelegationKey.parseDelimitedFrom(in));

      prog.setTotal(Phase.LOADING_FSIMAGE, currentStep, numTokens);
      Counter counter = prog.getCounter(Phase.LOADING_FSIMAGE, currentStep);
      for (int i = 0; i < numTokens; ++i) {
        tokens.add(SecretManagerSection.PersistToken.parseDelimitedFrom(in));
        counter.increment();
      }

      fsn.loadSecretManagerState(s, null, keys, null ,null, tokens);
    }


    private void loadFbCacheManagerSection(InputStream in, StartupProgress prog,
                                         Step currentStep) throws IOException {
      //ByteBuffer byteBuffer = parseFrom(in);
      FbCacheManagerSection cs = null;
      //    FbCacheManagerSection.getRootAsFbCacheManagerSection(byteBuffer);

      long numPools = cs.numPools();
      ArrayList<CachePoolInfoProto> pools = Lists
          .newArrayListWithCapacity((int)numPools);
      ArrayList<CacheDirectiveInfoProto> directives = Lists
          .newArrayListWithCapacity((int)cs.numDirectives());

      prog.setTotal(Phase.LOADING_FSIMAGE, currentStep, numPools);
      Counter counter = prog.getCounter(Phase.LOADING_FSIMAGE, currentStep);
      for (int i = 0; i < numPools; ++i) {
        pools.add(CachePoolInfoProto.parseDelimitedFrom(in));
        counter.increment();
      }
      for (int i = 0; i < cs.numDirectives(); ++i)
        directives.add(CacheDirectiveInfoProto.parseDelimitedFrom(in));
      fsn.getCacheManager().loadState(
          new CacheManager.PersistState(null, cs, pools, directives));
    }

    private void loadCacheManagerSection(InputStream in, StartupProgress prog,
        Step currentStep) throws IOException {
      CacheManagerSection s = CacheManagerSection.parseDelimitedFrom(in);
      int numPools = s.getNumPools();
      ArrayList<CachePoolInfoProto> pools = Lists
          .newArrayListWithCapacity(numPools);
      ArrayList<CacheDirectiveInfoProto> directives = Lists
          .newArrayListWithCapacity(s.getNumDirectives());
      prog.setTotal(Phase.LOADING_FSIMAGE, currentStep, numPools);
      Counter counter = prog.getCounter(Phase.LOADING_FSIMAGE, currentStep);
      for (int i = 0; i < numPools; ++i) {
        pools.add(CachePoolInfoProto.parseDelimitedFrom(in));
        counter.increment();
      }
      for (int i = 0; i < s.getNumDirectives(); ++i)
        directives.add(CacheDirectiveInfoProto.parseDelimitedFrom(in));
      fsn.getCacheManager().loadState(
          new CacheManager.PersistState(s, null, pools, directives));
    }

  }

  public static final class Saver {
    public static final int CHECK_CANCEL_INTERVAL = 4096;

    private final SaveNamespaceContext context;
    private final SaverContext saverContext;
    private long currentOffset = FSImageUtil.MAGIC_HEADER.length;
    private MD5Hash savedDigest;

    private FileChannel fileChannel;
    // OutputStream for the section data
    private OutputStream sectionOutputStream;
    private CompressionCodec codec;
    private OutputStream underlyingOutputStream;

    Saver(SaveNamespaceContext context) {
      this.context = context;
      this.saverContext = new SaverContext();
    }

    public MD5Hash getSavedDigest() {
      return savedDigest;
    }

    public SaveNamespaceContext getContext() {
      return context;
    }

    public SaverContext getSaverContext() {
      return saverContext;
    }

    public int commitFbSection(SectionName name, FlatBufferBuilder fbb) throws IOException {
      long oldOffset = currentOffset;
      flushSectionOutputStream();
      if (codec != null) {
        sectionOutputStream = codec.createOutputStream(underlyingOutputStream);
      } else {
        sectionOutputStream = underlyingOutputStream;
      }
      long length = fileChannel.position() - oldOffset;
      int sectionName = fbb.createString(name.name);
      long sectionOffset = currentOffset;
      currentOffset += length;
      return FbSection.createFbSection(fbb, sectionName, length, sectionOffset);
    }

    public void commitSection(FileSummary.Builder summary, SectionName name)
        throws IOException {
      long oldOffset = currentOffset;
      flushSectionOutputStream();
      if (codec != null) {
        sectionOutputStream = codec.createOutputStream(underlyingOutputStream);
      } else {
        sectionOutputStream = underlyingOutputStream;
      }
      long length = fileChannel.position() - oldOffset;
      summary.addSections(FileSummary.Section.newBuilder().setName(name.name)
          .setLength(length).setOffset(currentOffset));
      currentOffset += length;
    }

    private void flushSectionOutputStream() throws IOException {
      if (codec != null) {
        ((CompressorStream) sectionOutputStream).finish();
      }
      sectionOutputStream.flush();
    }

    void save(File file, FSImageCompression compression) throws IOException {
      FileOutputStream fout = new FileOutputStream(file);
      fileChannel = fout.getChannel();
      try {
        saveFbInternal(fout, compression, file.getAbsolutePath());
      } finally {
        fout.close();
      }
    }

    private static void saveFbFileSummary(OutputStream out,
                                             int serializedLength, byte[] bytes)
      throws IOException{
      DataOutputStream dos = new DataOutputStream(out);
      dos.writeInt(serializedLength);
      dos.write(bytes);
      int length = serializedLength + 4;
      dos.writeInt(length);
      dos.flush();
    }

    private static void saveFileSummary(OutputStream out, FileSummary summary)
        throws IOException {
      summary.writeDelimitedTo(out);
      int length = getOndiskTrunkSize(summary);
      byte[] lengthBytes = new byte[4];
      ByteBuffer.wrap(lengthBytes).asIntBuffer().put(length);
      out.write(lengthBytes);
    }

    private void saveFbInodes(FlatBufferBuilder fbb, ArrayList<Integer> list) throws IOException {
      FSImageFormatPBINode.Saver saver = new FSImageFormatPBINode.Saver(this, null, fbb);
      list.add(saver.serializeFbINodeSection(sectionOutputStream, fbb));
      list.add(saver.serializeFbINodeDirectorySection(sectionOutputStream, fbb));
      list.add(saver.serializeFbFilesUCSection(sectionOutputStream, fbb));
    }

    private void saveFbSnapshots(FlatBufferBuilder fbb, ArrayList<Integer> list)
        throws IOException {
      FSImageFormatPBSnapshot.Saver snapshotSaver = new FSImageFormatPBSnapshot.Saver(
          this, null, fbb,context, context.getSourceNamesystem());
      list.add(snapshotSaver.serializeFbSnapshotSection(sectionOutputStream, fbb));
      list.add(snapshotSaver.serializeFbSnapshotDiffSection(sectionOutputStream, fbb));
      list.add(snapshotSaver.serializeFbINodeReferenceSection(sectionOutputStream, fbb));
    }

    // abandon method.
    private void saveInodes(FileSummary.Builder summary) throws IOException {
      FSImageFormatPBINode.Saver saver = new FSImageFormatPBINode.Saver(this, summary, null);
      saver.serializeINodeSection(sectionOutputStream);
      saver.serializeINodeDirectorySection(sectionOutputStream);
      saver.serializeFilesUCSection(sectionOutputStream);
    }
    // abandon method
    private void saveSnapshots(FileSummary.Builder summary) throws IOException {
      FSImageFormatPBSnapshot.Saver snapshotSaver = new FSImageFormatPBSnapshot.Saver(
          this, summary, null,context, context.getSourceNamesystem());

      snapshotSaver.serializeSnapshotSection(sectionOutputStream);
      snapshotSaver.serializeSnapshotDiffSection(sectionOutputStream);
      snapshotSaver.serializeINodeReferenceSection(sectionOutputStream);
    }

    private void saveFbInternal(FileOutputStream fout, FSImageCompression compression, String filePath)
        throws IOException {

      ArrayList<Integer> listSection = new ArrayList<>();

      StartupProgress prog = NameNode.getStartupProgress();
      MessageDigest digester = MD5Hash.getDigester();

      underlyingOutputStream = new DigestOutputStream(new BufferedOutputStream(
              fout), digester);
      underlyingOutputStream.write(FSImageUtil.MAGIC_HEADER);
      fileChannel = fout.getChannel();

      FlatBufferBuilder fbb = new FlatBufferBuilder();
      long disk_version = FSImageUtil.FILE_VERSION;
      long layout_version = context.getSourceNamesystem().getEffectiveLayoutVersion();
      int code = 0;
      codec = compression.getImageCodec();
      if (codec != null) {
         code = fbb.createString(codec.getClass().getCanonicalName());
        sectionOutputStream = codec.createOutputStream(underlyingOutputStream);
      } else {
        code = fbb.createString("");
        sectionOutputStream = underlyingOutputStream;
      }
      listSection.add(saveFbNameSystemSection(fbb)); // success
      context.checkCancelled();
      Step step = new Step(StepType.INODES, filePath);
      prog.beginStep(Phase.SAVING_CHECKPOINT, step);
      saveFbInodes(fbb, listSection); // success
      saveFbSnapshots(fbb, listSection); // success
      prog.endStep(Phase.SAVING_CHECKPOINT, step);
      step = new Step(StepType.DELEGATION_TOKENS, filePath);
      prog.beginStep(Phase.SAVING_CHECKPOINT, step);
      listSection.add(saveFbSecretManagerSection(fbb)); // success
      prog.endStep(Phase.SAVING_CHECKPOINT, step);
      step = new Step(StepType.CACHE_POOLS, filePath);
      prog.beginStep(Phase.SAVING_CHECKPOINT, step);
      listSection.add(saveFbCacheManagerSection(fbb)); //success
      prog.endStep(Phase.SAVING_CHECKPOINT, step);
      listSection.add(saveFbStringTableSection(fbb));  // success
      flushSectionOutputStream();

      Integer[] data = new Integer[listSection.size()];
      for (int i = 0; i < data.length ; i++) {
        data[i] = listSection.get(i);
      }

      int sections = FbFileSummary.createSectionsVector(fbb, ArrayUtils.toPrimitive(data));
      int end = FbFileSummary.createFbFileSummary
                    (fbb, disk_version, layout_version, code, sections);
      FbFileSummary.finishFbFileSummaryBuffer(fbb, end);
      byte[] bytes = fbb.sizedByteArray();
      saveFbFileSummary(underlyingOutputStream, bytes.length, bytes);
      underlyingOutputStream.close();
      savedDigest = new MD5Hash(digester.digest());
    }

    // saveImage main method
    private void saveInternal(FileOutputStream fout,
        FSImageCompression compression, String filePath) throws IOException {
      StartupProgress prog = NameNode.getStartupProgress();
      MessageDigest digester = MD5Hash.getDigester();

      underlyingOutputStream = new DigestOutputStream(new BufferedOutputStream(
          fout), digester);
      underlyingOutputStream.write(FSImageUtil.MAGIC_HEADER);

      fileChannel = fout.getChannel();

      // Will Revise
      FileSummary.Builder b = FileSummary.newBuilder()
          .setOndiskVersion(FSImageUtil.FILE_VERSION)
          .setLayoutVersion(
              context.getSourceNamesystem().getEffectiveLayoutVersion());

      codec = compression.getImageCodec();
      if (codec != null) {
        b.setCodec(codec.getClass().getCanonicalName());
        sectionOutputStream = codec.createOutputStream(underlyingOutputStream);
      } else {
        sectionOutputStream = underlyingOutputStream;
      }

      saveNameSystemSection(b);
      // Check for cancellation right after serializing the name system section.
      // Some unit tests, such as TestSaveNamespace#testCancelSaveNameSpace
      // depends on this behavior.
      context.checkCancelled();

      Step step = new Step(StepType.INODES, filePath);
      prog.beginStep(Phase.SAVING_CHECKPOINT, step);
      saveInodes(b);
      saveSnapshots(b);
      prog.endStep(Phase.SAVING_CHECKPOINT, step);

      step = new Step(StepType.DELEGATION_TOKENS, filePath);
      prog.beginStep(Phase.SAVING_CHECKPOINT, step);
      saveSecretManagerSection(b);
      prog.endStep(Phase.SAVING_CHECKPOINT, step);

      step = new Step(StepType.CACHE_POOLS, filePath);
      prog.beginStep(Phase.SAVING_CHECKPOINT, step);
      saveCacheManagerSection(b);
      prog.endStep(Phase.SAVING_CHECKPOINT, step);

      saveStringTableSection(b);

      // We use the underlyingOutputStream to write the header. Therefore flush
      // the buffered stream (which is potentially compressed) first.
      flushSectionOutputStream();

      FileSummary summary = b.build();
      saveFileSummary(underlyingOutputStream, summary);
      underlyingOutputStream.close();
      savedDigest = new MD5Hash(digester.digest());
    }

    private int saveFbSecretManagerSection(FlatBufferBuilder fbb) throws IOException {
      final FSNamesystem fsn = context.getSourceNamesystem();
      DelegationTokenSecretManager.SecretManagerState state = fsn
          .saveSecretManagerState();

      ByteBuffer byteBuffer = state.fbSection.getByteBuffer();
      int size = byteBuffer.capacity() - byteBuffer.position();
      byte[] bytes = new byte[size];
      byteBuffer.get(bytes);
      writeTo(bytes, bytes.length, sectionOutputStream);

      for(FbDelegationKey fbK : state.fbKeys) {
        ByteBuffer byteBuffer1 = fbK.keyAsByteBuffer(); // confusing...
        int size1 = byteBuffer1.capacity() - byteBuffer1.position();
        byte[] bytes1 = new byte[size1];
        byteBuffer1.get(bytes1);
        writeTo(bytes1, bytes1.length, sectionOutputStream);
      }

      for (FbPersistToken fbT : state.fbTokens) {
        ByteBuffer byteBuffer2 = fbT.realUserAsByteBuffer(); // confus...
        int size2 = byteBuffer2.capacity() - byteBuffer2.position();
        byte[] bytes2 = new byte[size2];
        byteBuffer2.get(bytes2);
        writeTo(bytes2, bytes2.length, sectionOutputStream);
      }
     return commitFbSection(SectionName.SECRET_MANAGER, fbb);
    }

    private void saveSecretManagerSection(FileSummary.Builder summary)
        throws IOException {
      final FSNamesystem fsn = context.getSourceNamesystem();
      DelegationTokenSecretManager.SecretManagerState state = fsn
          .saveSecretManagerState();
      state.section.writeDelimitedTo(sectionOutputStream);
      for (SecretManagerSection.DelegationKey k : state.keys)
        k.writeDelimitedTo(sectionOutputStream);

      for (SecretManagerSection.PersistToken t : state.tokens)
        t.writeDelimitedTo(sectionOutputStream);

      commitSection(summary, SectionName.SECRET_MANAGER);
    }

    private int saveFbCacheManagerSection(FlatBufferBuilder fbb)
        throws IOException {
      final FSNamesystem fsn = context.getSourceNamesystem();
      CacheManager.PersistState state = fsn.getCacheManager().saveState();
      ByteBuffer byteBuffer = state.fbSection.getByteBuffer();
      int size = byteBuffer.capacity() - byteBuffer.position();
      byte[] bytes = new byte[size];
      byteBuffer.get(bytes);
      writeTo(bytes, bytes.length, sectionOutputStream);

      for (CachePoolInfoProto p : state.pools)
        p.writeDelimitedTo(sectionOutputStream);

      for (CacheDirectiveInfoProto p : state.directives)
        p.writeDelimitedTo(sectionOutputStream);

     return commitFbSection(SectionName.CACHE_MANAGER, fbb);
    }

    private void saveCacheManagerSection(FileSummary.Builder summary)
        throws IOException {
      final FSNamesystem fsn = context.getSourceNamesystem();
      CacheManager.PersistState state = fsn.getCacheManager().saveState();
      state.section.writeDelimitedTo(sectionOutputStream);

      for (CachePoolInfoProto p : state.pools)
        p.writeDelimitedTo(sectionOutputStream);

      for (CacheDirectiveInfoProto p : state.directives)
        p.writeDelimitedTo(sectionOutputStream);

      commitSection(summary, SectionName.CACHE_MANAGER);
    }


    private int saveFbNameSystemSection(FlatBufferBuilder fbb) throws IOException{
      final FSNamesystem fsn = context.getSourceNamesystem();
      BlockIdManager blockIdManager = fsn.getBlockIdManager();
      FlatBufferBuilder nsFbb = new FlatBufferBuilder();
      FbNameSystemSection.startFbNameSystemSection(nsFbb);
      FbNameSystemSection.addGenstampV1(nsFbb, blockIdManager.getGenerationStampV1());
      FbNameSystemSection.addGenstampV1Limit(nsFbb, blockIdManager.getGenerationStampV1Limit());
      FbNameSystemSection.addGenstampV2(nsFbb, blockIdManager.getGenerationStampV2());
      FbNameSystemSection.addLastAllocatedBlockId(nsFbb, blockIdManager.getLastAllocatedBlockId());
      FbNameSystemSection.addTransactionId(nsFbb, context.getTxId());
      FbNameSystemSection.addNamespaceId(nsFbb, fsn.unprotectedGetNamespaceInfo().getNamespaceID());
      if (fsn.isRollingUpgrade()) {
        FbNameSystemSection.addRollingUpgradeStartTime(nsFbb, fsn.getRollingUpgradeInfo().getStartTime());
      }
      int offset = FbNameSystemSection.endFbNameSystemSection(nsFbb);
      FbNameSystemSection.finishFbNameSystemSectionBuffer(nsFbb, offset);
      byte[] bytes = nsFbb.sizedByteArray();
      writeTo(bytes, bytes.length, sectionOutputStream);
      return commitFbSection(SectionName.NS_INFO ,fbb);
    }

    public static void writeTo(byte[] b, int serialLength , OutputStream outputStream) throws IOException {
      DataOutputStream dos = new DataOutputStream(outputStream);
      dos.writeInt(serialLength);
      dos.write(b);
      dos.flush();
    }

    private void saveNameSystemSection(FileSummary.Builder summary)
        throws IOException {
      final FSNamesystem fsn = context.getSourceNamesystem();
      OutputStream out = sectionOutputStream;
      BlockIdManager blockIdManager = fsn.getBlockIdManager();
      NameSystemSection.Builder b = NameSystemSection.newBuilder()
          .setGenstampV1(blockIdManager.getGenerationStampV1())
          .setGenstampV1Limit(blockIdManager.getGenerationStampV1Limit())
          .setGenstampV2(blockIdManager.getGenerationStampV2())
          .setLastAllocatedBlockId(blockIdManager.getLastAllocatedBlockId())
          .setTransactionId(context.getTxId());

      // We use the non-locked version of getNamespaceInfo here since
      // the coordinating thread of saveNamespace already has read-locked
      // the namespace for us. If we attempt to take another readlock
      // from the actual saver thread, there's a potential of a
      // fairness-related deadlock. See the comments on HDFS-2223.
      b.setNamespaceId(fsn.unprotectedGetNamespaceInfo().getNamespaceID());
      if (fsn.isRollingUpgrade()) {
        b.setRollingUpgradeStartTime(fsn.getRollingUpgradeInfo().getStartTime());
      }
      NameSystemSection s = b.build();
      s.writeDelimitedTo(out);

      commitSection(summary, SectionName.NS_INFO);
    }


    private int saveFbStringTableSection(FlatBufferBuilder fbb)
        throws IOException {
      OutputStream out = sectionOutputStream;
      FlatBufferBuilder stsfbb = new FlatBufferBuilder();
      FlatBufferBuilder entryfbb = new FlatBufferBuilder();
      int inv = FbStringTableSection.createFbStringTableSection(stsfbb, saverContext.stringMap.size());
      FbStringTableSection.finishFbStringTableSectionBuffer(stsfbb, inv);
      byte[] bytes = stsfbb.sizedByteArray();
      writeTo(bytes, bytes.length, out);

      for (Entry<String, Integer> e : saverContext.stringMap.entrySet()) {
        int offset = FbEntry.createFbEntry(entryfbb, e.getValue(), entryfbb.createString(e.getKey()));
        FbEntry.finishFbEntryBuffer(entryfbb, offset);
        byte[] bytes1 = entryfbb.sizedByteArray();
        writeTo(bytes1, bytes1.length, out);
      }
      return commitFbSection(SectionName.STRING_TABLE, fbb);
    }


    private int saveFbStringTableSectionV2(FlatBufferBuilder fbb)
        throws IOException {
      OutputStream out = sectionOutputStream;

      int numEntry = saverContext.stringMap.size();
      FbStringTableSection.createFbStringTableSection(fbb, numEntry);

      ByteBuffer byteBuffer = fbb.dataBuffer();
      int serializedLength = byteBuffer.capacity() - byteBuffer.position();
      byte[] bytes = new byte[serializedLength];
      byteBuffer.get(bytes);
      DataOutputStream dos = new DataOutputStream(out);
      dos.write(serializedLength);
      dos.write(bytes);
      dos.flush();
      for (Entry<String, Integer> e : saverContext.stringMap.entrySet()) {
        FbEntry.createFbEntry(fbb, e.getValue(), fbb.createString(e.getKey()));
        ByteBuffer byteBuffer1 = fbb.dataBuffer();
        byteBuffer1 = fbb.dataBuffer();
        int serializedLength1 = byteBuffer1.capacity() - byteBuffer1.position();
        byte[] bytes1 = new byte[serializedLength1];
        byteBuffer1.get(bytes1);
        DataOutputStream dos1 = new DataOutputStream(out);
        dos1.write(serializedLength);
        dos1.write(bytes);
        dos1.flush();
      }
      return commitFbSection(SectionName.STRING_TABLE, fbb);
    }

    private void saveStringTableSection(FileSummary.Builder summary)
        throws IOException {
      OutputStream out = sectionOutputStream;
      StringTableSection.Builder b = StringTableSection.newBuilder()
          .setNumEntry(saverContext.stringMap.size());
      b.build().writeDelimitedTo(out);
      for (Entry<String, Integer> e : saverContext.stringMap.entrySet()) {
        StringTableSection.Entry.Builder eb = StringTableSection.Entry
            .newBuilder().setId(e.getValue()).setStr(e.getKey());
        eb.build().writeDelimitedTo(out);
      }
      commitSection(summary, SectionName.STRING_TABLE);
    }
  }

  /**
   * Supported section name. The order of the enum determines the order of
   * loading.
   */
  public enum SectionName {
    NS_INFO("NS_INFO"),
    STRING_TABLE("STRING_TABLE"),
    EXTENDED_ACL("EXTENDED_ACL"),
    INODE("INODE"),
    INODE_REFERENCE("INODE_REFERENCE"),
    SNAPSHOT("SNAPSHOT"),
    INODE_DIR("INODE_DIR"),
    FILES_UNDERCONSTRUCTION("FILES_UNDERCONSTRUCTION"),
    SNAPSHOT_DIFF("SNAPSHOT_DIFF"),
    SECRET_MANAGER("SECRET_MANAGER"),
    CACHE_MANAGER("CACHE_MANAGER");

    private static final SectionName[] values = SectionName.values();

    public static SectionName fromString(String name) {
      for (SectionName n : values) {
        if (n.name.equals(name))
          return n;
      }
      return null;
    }

    private final String name;

    private SectionName(String name) {
      this.name = name;
    }
  }

  private static int getFbOndiskTrunkSize(Table table) {
    return 4 + table.getByteBuffer().capacity() - table.getByteBuffer().position();
  }

  private static int getOndiskTrunkSize(com.google.protobuf.GeneratedMessage s) {
    return CodedOutputStream.computeRawVarint32Size(s.getSerializedSize())
        + s.getSerializedSize();
  }

  private FSImageFormatProtobuf() {
  }
}
