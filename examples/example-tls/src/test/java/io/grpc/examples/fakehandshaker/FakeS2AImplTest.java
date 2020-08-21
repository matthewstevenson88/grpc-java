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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import javax.annotation.Nullable;

import io.grpc.Server;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;

import com.google.protobuf.ByteString;
import com.google.net.grpc.s2a.handshaker.SessionReq;
import com.google.net.grpc.s2a.handshaker.SessionResp;
import com.google.net.grpc.s2a.handshaker.SessionStatus;
import com.google.net.grpc.s2a.handshaker.SessionNextReq;
import com.google.net.grpc.s2a.handshaker.ServerSessionStartReq;
import com.google.net.grpc.s2a.handshaker.ClientSessionStartReq;
import com.google.net.grpc.s2a.handshaker.TLSVersion;
import com.google.net.grpc.s2a.handshaker.Ciphersuite;
import com.google.net.grpc.s2a.handshaker.ResumptionTicketReq;


/** Unit tests for {@link FakeS2AImpl}. */
@RunWith(JUnit4.class)
public class FakeS2AImplTest {
  private StreamObserver<SessionResp> initRespStream;
  private FakeS2AImpl fakeS2A;
  private StreamObserver<SessionReq> setUpReq;
  @Nullable private SessionResp respLogger;
  @Nullable private Throwable error;

  @Before
  public void setUp() throws Exception {
    error=null;
    respLogger=null;
    initRespStream = new StreamObserver<SessionResp>(){
      public void onNext(SessionResp resp){
        respLogger = resp;
      }
      public void onError(Throwable t){
        error = t;
      }
      public void onCompleted(){}
    };

  }

  @Test
  public void processClientStart_failsBecauseEmptyClientStart() throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq emptyReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().build()).build();
    setUpReq.onNext(emptyReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value()); 
    assertNull(error);
  }

  @Test
  public void processClientStart_failsBecauseWrongApplicationProtocol() throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq nonGRPCReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().addApplicationProtocols("").build()).build();
    setUpReq.onNext(nonGRPCReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value());
    assertNull(error);
  }

  @Test
  public void processClientStart_failsBecauseWrongMaxTLSVersion()throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq incorrectTLSReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().setMaxTlsVersion(TLSVersion.TLS1_2).build()).build();
    setUpReq.onNext(incorrectTLSReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value());
    assertNull(error);
  }

  @Test
  public void processClientStart_failsBecauseWrongMinTLSVersion()throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq incorrectTLSReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().setMaxTlsVersion(TLSVersion.TLS1_3)
      .setMinTlsVersion(TLSVersion.TLS1_2).build()).build();
    setUpReq.onNext(incorrectTLSReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value());
    assertNull(error);
  }

  @Test 
  public void processClientStart_success()throws Exception{
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq passReq = SessionReq.newBuilder().setClientStart(
      ClientSessionStartReq.newBuilder().addApplicationProtocols(FakeS2AImpl.GRPC_APPLICATION_PROTOCOL)
      .setMinTlsVersion(TLSVersion.TLS1_3).setMaxTlsVersion(TLSVersion.TLS1_3)
      .addTlsCiphersuites(Ciphersuite.AES_128_GCM_SHA256).build()).build();
    setUpReq.onNext(passReq);
    assertTrue(respLogger.getStatus().getCode() == Code.OK.value());
    assertTrue(respLogger.getBytesConsumed() == 0);
    assertTrue(respLogger.getOutFrames().equals(ByteString.copyFrom(FakeS2AImpl.CLIENT_HELLO_FRAME.getBytes())));
    assertNull(error);
  }

  @Test
  public void processServerStart_failsBecauseEmptyServerStart() throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq emptyReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().build()).build();
    setUpReq.onNext(emptyReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value()); 
    assertNull(error);
  }

  @Test
  public void processServerStart_failsBecauseWrongApplicationProtocol() throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq nonGRPCReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().addApplicationProtocols("").build()).build();
    setUpReq.onNext(nonGRPCReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value());
    assertNull(error);
  }

  @Test
  public void processServerStart_failsBecauseWrongMaxTLSVersion() throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq incorrectTLSReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().setMaxTlsVersion(TLSVersion.TLS1_2).build()).build();
    setUpReq.onNext(incorrectTLSReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value());
    assertNull(error);
  }

    @Test
  public void processServerStart_failsBecauseWrongMinTLSVersion() throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq incorrectTLSReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().setMaxTlsVersion(TLSVersion.TLS1_3)
      .setMinTlsVersion(TLSVersion.TLS1_2).build()).build();
    setUpReq.onNext(incorrectTLSReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value());
    assertNull(error);
  }

  @Test
  public void processServerStart_success() throws Exception {
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq passReq = SessionReq.newBuilder().setServerStart(
      ServerSessionStartReq.newBuilder().addApplicationProtocols(FakeS2AImpl.GRPC_APPLICATION_PROTOCOL)
      .setMinTlsVersion(TLSVersion.TLS1_3).setMaxTlsVersion(TLSVersion.TLS1_3)
      .addTlsCiphersuites(Ciphersuite.AES_128_GCM_SHA256)
      .setInBytes(ByteString.copyFrom(FakeS2AImpl.GRPC_APPLICATION_PROTOCOL.getBytes())).build()).build();
    setUpReq.onNext(passReq);
    assertTrue(respLogger.getStatus().getCode() == Code.OK.value());
    assertTrue(respLogger.getOutFrames().equals(ByteString.copyFrom(FakeS2AImpl.SERVER_FRAME.getBytes())));
    assertTrue(respLogger.getBytesConsumed() == FakeS2AImpl.CLIENT_HELLO_FRAME.length());
    assertNull(error);
  }

  @Test
  public void processNext_failBecauseWrongState() throws Exception {
    fakeS2A = new FakeS2AImpl(FakeS2AImpl.HandshakeState.INITIAL);
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq emptyReq = SessionReq.newBuilder().setNext(
      SessionNextReq.newBuilder().build()).build();
    setUpReq.onNext(emptyReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value()); 
    assertNull(error);
  }

  @Test
  public void processNext_failBecauseEmptySessionNext() throws Exception{
    fakeS2A = new FakeS2AImpl(FakeS2AImpl.HandshakeState.STARTED);
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq emptyReq = SessionReq.newBuilder().setNext(
      SessionNextReq.newBuilder().build()).build();
    setUpReq.onNext(emptyReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value()); 
    assertNull(error);
  }

  @Test 
  public void processNext_failBecauseWrongHelloFrame() throws Exception{
    fakeS2A = new FakeS2AImpl(FakeS2AImpl.HandshakeState.STARTED);
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq emptyReq = SessionReq.newBuilder().setNext(
      SessionNextReq.newBuilder().setInBytes(ByteString.copyFrom("".getBytes())).build()).build();
    setUpReq.onNext(emptyReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value()); 
    assertNull(error);
  }

  @Test 
  public void processNext_failBecauseWrongFinishedFrame() throws Exception{
    fakeS2A = new FakeS2AImpl(FakeS2AImpl.HandshakeState.SENT);
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq emptyReq = SessionReq.newBuilder().setNext(
      SessionNextReq.newBuilder().setInBytes(ByteString.copyFrom("".getBytes())).build()).build();
    setUpReq.onNext(emptyReq);
    assertFalse(respLogger.getStatus().getCode() == Code.OK.value()); 
    assertNull(error);
  }

  @Test
  public void processNext_passHandshakeStarted() throws Exception{
    fakeS2A = new FakeS2AImpl(FakeS2AImpl.HandshakeState.STARTED);
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq emptyReq = SessionReq.newBuilder().setNext(
      SessionNextReq.newBuilder().setInBytes(
        ByteString.copyFrom(FakeS2AImpl.CLIENT_HELLO_FRAME.getBytes())).build()).build();
    setUpReq.onNext(emptyReq);
    assertTrue(respLogger.getStatus().getCode() == Code.OK.value()); 
    assertTrue(respLogger.getOutFrames().equals(ByteString.copyFrom(FakeS2AImpl.SERVER_FRAME.getBytes())));
    assertTrue(respLogger.getBytesConsumed() == FakeS2AImpl.CLIENT_HELLO_FRAME.length());
    assertNull(error);
  }

  @Test
  public void processNext_passHandshakeSent() throws Exception{
    fakeS2A = new FakeS2AImpl(FakeS2AImpl.HandshakeState.SENT);
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq emptyReq = SessionReq.newBuilder().setNext(
      SessionNextReq.newBuilder().setInBytes(
        ByteString.copyFrom(FakeS2AImpl.CLIENT_FINISHED_FRAME.getBytes())).build()).build();
    setUpReq.onNext(emptyReq);
    assertTrue(respLogger.getStatus().getCode() == Code.OK.value()); 
    assertTrue(respLogger.getBytesConsumed() == FakeS2AImpl.CLIENT_FINISHED_FRAME.length());
    assertTrue(respLogger.getResult()!=null);
    assertNull(error);
  }

  @Test 
  public void processResumption_success() throws Exception{
    fakeS2A = FakeS2AImpl.create();
    setUpReq = fakeS2A.setUpSession(initRespStream);
    SessionReq passReq= SessionReq.newBuilder()
     .setResumptionTicket(ResumptionTicketReq.newBuilder().build()).build();
    setUpReq.onNext(passReq);
    assertTrue(respLogger.getStatus().getCode() == Code.OK.value());
    assertNull(error);
  }
}
