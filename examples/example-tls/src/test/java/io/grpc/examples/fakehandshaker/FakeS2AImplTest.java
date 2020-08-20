/*
 * Copyright 2020 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.examples.fakehandshaker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import io.grpc.Server;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;

import io.grpc.examples.helloworldtls.*;
import com.google.protobuf.*;
import com.google.net.grpc.s2a.handshaker.*;


/** Unit tests for {@link FakeS2AImpl}. */
@RunWith(JUnit4.class)
public class FakeS2AImplTest {
  StreamObserver<SessionResp> initRespStream;
  SessionResp respLogger=null;

  @Before
  public void setUp() throws Exception {
    this.initRespStream = new StreamObserver<SessionResp>(){
      public void onNext(SessionResp resp){
        respLogger = resp;
      }
      public void onError(Throwable t){}
      public void onCompleted(){}
    };
  }

  @Test
  public void TestProcessClientStart() throws Exception {
    FakeS2AImpl fakeS2A = new FakeS2AImpl();
    StreamObserver<SessionReq> setUpReq = fakeS2A.setUpSession(this.initRespStream);

    SessionReq emptyReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().build()).build();
    setUpReq.onNext(emptyReq);
    assertFalse(this.respLogger.getStatus().getCode() == Code.OK.value()); 

    SessionReq nonGRPCReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().addApplicationProtocols("").build()).build();
    setUpReq.onNext(nonGRPCReq);
    assertFalse(this.respLogger.getStatus().getCode() == Code.OK.value());

    SessionReq incorrectTLSReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().setMaxTlsVersion(TLSVersion.TLS1_2).build()).build();
    setUpReq.onNext(incorrectTLSReq);
    assertFalse(this.respLogger.getStatus().getCode() == Code.OK.value());

    SessionReq passReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().addApplicationProtocols(FakeS2AImpl.grpcAppProtocol)
      .setMinTlsVersion(TLSVersion.TLS1_2).setMaxTlsVersion(TLSVersion.TLS1_3)
      .addTlsCiphersuites(Ciphersuite.AES_128_GCM_SHA256).build()).build();
    setUpReq.onNext(passReq);
    assertTrue(this.respLogger.getStatus().getCode() == Code.OK.value());
    assertTrue(this.respLogger.getBytesConsumed() == 0);
    assertTrue(this.respLogger.getOutFrames().equals(ByteString.copyFrom(FakeS2AImpl.clientHelloFrame.getBytes())));
  }


  public void TestProcessServerStart() throws Exception {
    FakeS2AImpl fakeS2A = new FakeS2AImpl();
    StreamObserver<SessionReq> setUpReq = fakeS2A.setUpSession(this.initRespStream);

    SessionReq emptyReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().build()).build();
    setUpReq.onNext(emptyReq);
    assertFalse(this.respLogger.getStatus().getCode() == Code.OK.value()); 

    SessionReq nonGRPCReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().addApplicationProtocols("").build()).build();
    setUpReq.onNext(nonGRPCReq);
    assertFalse(this.respLogger.getStatus().getCode() == Code.OK.value());

    SessionReq incorrectTLSReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().setMaxTlsVersion(TLSVersion.TLS1_2).build()).build();
    setUpReq.onNext(incorrectTLSReq);
    assertFalse(this.respLogger.getStatus().getCode() == Code.OK.value());

    SessionReq passReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().addApplicationProtocols(FakeS2AImpl.grpcAppProtocol)
      .setMinTlsVersion(TLSVersion.TLS1_2).setMaxTlsVersion(TLSVersion.TLS1_3)
      .addTlsCiphersuites(Ciphersuite.AES_128_GCM_SHA256)
      .setInBytes(ByteString.copyFrom(FakeS2AImpl.grpcAppProtocol.getBytes())).build()).build();
    setUpReq.onNext(passReq);
    assertTrue(this.respLogger.getStatus().getCode() == Code.OK.value());
    assertTrue(this.respLogger.getOutFrames().equals(ByteString.copyFrom(FakeS2AImpl.serverFrame.getBytes())));
    assertTrue(this.respLogger.getBytesConsumed() == FakeS2AImpl.clientHelloFrame.length());
  }
}
