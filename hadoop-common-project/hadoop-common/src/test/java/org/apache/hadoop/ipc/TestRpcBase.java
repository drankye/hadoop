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

package org.apache.hadoop.ipc;

import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import org.apache.hadoop.ipc.protobuf.TestProtos;
import org.apache.hadoop.ipc.protobuf.TestRpcServiceProtos;
import org.junit.Assert;

import java.net.URISyntaxException;

/** Test facilities for unit tests for RPC. */
public class TestRpcBase {

  protected static final String ADDRESS = "0.0.0.0";

  @ProtocolInfo(protocolName = "testProto", protocolVersion = 1)
  public interface TestRpcService
      extends TestRpcServiceProtos.TestProtobufRpcProto.BlockingInterface {
  }

  public static class PBServerImpl implements TestRpcService {

    @Override
    public TestProtos.EmptyResponseProto ping(RpcController unused,
                TestProtos.EmptyRequestProto request) throws ServiceException {
      // Ensure clientId is received
      byte[] clientId = Server.getClientId();
      Assert.assertNotNull(clientId);
      Assert.assertEquals(ClientId.BYTE_LENGTH, clientId.length);
      return TestProtos.EmptyResponseProto.newBuilder().build();
    }

    @Override
    public TestProtos.EchoResponseProto echo(RpcController unused,
                                         TestProtos.EchoRequestProto request)
        throws ServiceException {
      return TestProtos.EchoResponseProto.newBuilder().setMessage(request.getMessage())
          .build();
    }

    @Override
    public TestProtos.EmptyResponseProto error(RpcController unused,
                                               TestProtos.EmptyRequestProto request) throws ServiceException {
      throw new ServiceException("error", new RpcServerException("error"));
    }

    @Override
    public TestProtos.EmptyResponseProto error2(RpcController unused,
                                                TestProtos.EmptyRequestProto request) throws ServiceException {
      throw new ServiceException("error", new URISyntaxException("",
          "testException"));
    }
  }
}
