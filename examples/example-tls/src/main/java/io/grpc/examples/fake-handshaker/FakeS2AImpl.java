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
package io.grpc.examples.helloworldtls;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.net.grpc.s2a.handshaker.*;
import com.google.protobuf.*;

import io.grpc.Server;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import java.util.logging.Level;


public class FakeS2AImpl extends S2AServiceGrpc.S2AServiceImplBase {
  private static final Logger logger = Logger.getLogger(FakeS2AImpl.class.getName());
  private HandshakeState state;
  private Identity peerIdentity;
  private Identity localIdentity;
  private boolean assistingClient;


  public static final String GRPC_APP_PROTOCOL = "grpc";
  public static final String CLIENT_HELLO_FRAME = "ClientHello";
  public static final String CLIENT_FINISHED_FRAME = "ClientFinished";
  public static final String SERVER_FRAME = "ServerHelloAndFinished";

  private static final String IN_KEY  = "kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk";
  private static final String OUT_KEY = "jjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjj";

  public enum HandshakeState {
    INITIAL,
    STARTED,
    SENT,
    COMPLETED
  }

  private FakeS2AImpl(){
    state=HandshakeState.INITIAL;
  }

  public static FakeS2AImpl create(){
    return new FakeS2AImpl();
  }

  public void setState(HandshakeState s){
    state = s;
  }

  @Override
  public StreamObserver<SessionReq> setUpSession(final StreamObserver<SessionResp> stream) {
    return new StreamObserver<SessionReq>(){
      @Override
      public void onNext(SessionReq req){
        SessionResp resp = null;
        switch (req.getReqOneofCase()){
          case CLIENT_START:
            resp = processClientStart(req);
            break;
          case SERVER_START:
            resp = processServerStart(req);
            break;
          case NEXT:
            resp = processNext(req);
            break;
          case RESUMPTION_TICKET:
            resp = processResumption(req);
            break;
          default:
            logger.log(Level.FINE,"Session request has unexpected type: "+req.getClass());
        }
        stream.onNext(resp);
      }

      @Override
      public void onError(Throwable t) {
        logger.log(Level.FINE, "Encountered error in routeChat", t);
      }

      @Override
      public void onCompleted() {
        stream.onCompleted();
        logger.log(Level.FINE, "Stream is complete");
      }

      // processClientStart processes a ClientSessionStartRequest.
      SessionResp processClientStart(SessionReq req){
        checkNotNull(req, "SessionReq should not be null");
        SessionResp.Builder resp = SessionResp.newBuilder();
        if (state != HandshakeState.INITIAL){
          resp.setStatus(SessionStatus.newBuilder().setCode(Code.FAILED_PRECONDITION.value())
          .setDetails("client start handshaker not in initial state").build());
          return resp.build();
        }
        if (req.getClientStart().getApplicationProtocolsList().size() != 1 ||
          req.getClientStart().getApplicationProtocols(0) != GRPC_APP_PROTOCOL){
           resp.setStatus(SessionStatus.newBuilder().setCode(Code.INVALID_ARGUMENT.value())
           .setDetails("application protocol was not grpc").build());
          return resp.build();
        }
        if (req.getClientStart().getMaxTlsVersion() != TLSVersion.TLS1_3){
          resp.setStatus(SessionStatus.newBuilder().setCode(Code.INVALID_ARGUMENT.value())
           .setDetails("max TLS version must be 1.3").build());
          return resp.build();
        }
        if (req.getClientStart().getMinTlsVersion() != TLSVersion.TLS1_3){
          resp.setStatus(SessionStatus.newBuilder().setCode(Code.INVALID_ARGUMENT.value())
           .setDetails("min TLS version must be 1.3").build());
          return resp.build();
        }
        resp.setOutFrames(ByteString.copyFrom(CLIENT_HELLO_FRAME.getBytes()));
        resp.setBytesConsumed(0);
        resp.setStatus(SessionStatus.newBuilder().setCode(Code.OK.value()).build());
        localIdentity = req.getClientStart().getLocalIdentity();
        if (req.getClientStart().getTargetIdentitiesList().size() > 0 ){
          peerIdentity = req.getClientStart().getTargetIdentities(0);
        }
        assistingClient = true;
        state = HandshakeState.SENT;
        return resp.build();
      }

      // processServerStart processes a ServerSessionStartRequest.
      SessionResp processServerStart(SessionReq req){
        checkNotNull(req, "SessionReq should not be null");
        SessionResp.Builder resp = SessionResp.newBuilder();
        if (state != HandshakeState.INITIAL){
          resp.setStatus(SessionStatus.newBuilder().setCode(Code.FAILED_PRECONDITION.value())
           .setDetails("server start handshaker not in initial state").build());
          return resp.build();
        }
        if (req.getServerStart().getApplicationProtocolsList().size() != 1 ||
          req.getServerStart().getApplicationProtocols(0) != GRPC_APP_PROTOCOL){
          resp.setStatus(SessionStatus.newBuilder().setCode(Code.INVALID_ARGUMENT.value())
           .setDetails("application protocol was not grpc").build());
          return resp.build();
        }
        if( req.getServerStart().getMaxTlsVersion() != TLSVersion.TLS1_3){
          resp.setStatus(SessionStatus.newBuilder().setCode(Code.INVALID_ARGUMENT.value())
           .setDetails("max TLS version must be 1.3").build());
          return resp.build();
        }
        if( req.getServerStart().getMinTlsVersion() != TLSVersion.TLS1_3){
          resp.setStatus(SessionStatus.newBuilder().setCode(Code.INVALID_ARGUMENT.value())
           .setDetails("min TLS version must be 1.3").build());
          return resp.build();
        }
        if (req.getServerStart().getInBytes().size() == 0) {
          resp.setBytesConsumed(0);
          state = HandshakeState.STARTED;
        } else if (req.getServerStart().getInBytes().equals(ByteString.copyFrom(GRPC_APP_PROTOCOL.getBytes()))){
          resp.setOutFrames(ByteString.copyFrom(SERVER_FRAME.getBytes()));
          resp.setBytesConsumed(CLIENT_HELLO_FRAME.length());
          state = HandshakeState.SENT;
        } else {
          resp.setStatus(SessionStatus.newBuilder().setCode(Code.INTERNAL.value())
           .setDetails("server start request did not have the correct input bytes").build());
          return resp.build();
        }

        resp.setStatus(SessionStatus.newBuilder().setCode(Code.OK.value()).build());
        if (req.getServerStart().getLocalIdentitiesList().size() > 0) {
          peerIdentity = req.getServerStart().getLocalIdentities(0);
        }
        assistingClient = false;
        return resp.build();
      }

      // processNext processes a SessionNext request.
      SessionResp processNext(SessionReq req){
        checkNotNull(req, "SessionReq should not be null");
        SessionResp.Builder resp = SessionResp.newBuilder();
        if (assistingClient) {
          if (state != HandshakeState.SENT) {
            resp.setStatus(SessionStatus.newBuilder().setCode(Code.FAILED_PRECONDITION.value())
             .setDetails("client handshaker was not in sent state").build());
            return resp.build();
          }
          if (!req.getNext().getInBytes().equals(ByteString.copyFrom(SERVER_FRAME.getBytes()))){
            resp.setStatus(SessionStatus.newBuilder().setCode(Code.INTERNAL.value())
             .setDetails("client request did not match server frame").build());
            return resp.build();
          }
          resp.setOutFrames(ByteString.copyFrom(CLIENT_FINISHED_FRAME.getBytes()));
          resp.setBytesConsumed(SERVER_FRAME.length());
          state = HandshakeState.COMPLETED;
        } else {
          if (state == HandshakeState.STARTED){
            if (!req.getNext().getInBytes().equals(ByteString.copyFrom(CLIENT_HELLO_FRAME.getBytes()))){
              resp.setStatus(SessionStatus.newBuilder().setCode(Code.INTERNAL.value())
               .setDetails("server request did not match client hello frame").build());
              return resp.build();
            }
            resp.setOutFrames(ByteString.copyFrom(SERVER_FRAME.getBytes()));
            resp.setBytesConsumed(CLIENT_HELLO_FRAME.length());
            state = HandshakeState.SENT;
          } else if(state == HandshakeState.SENT) {
            if (!req.getNext().getInBytes().equals(ByteString.copyFrom(CLIENT_FINISHED_FRAME.getBytes()))){
              resp.setStatus(SessionStatus.newBuilder().setCode(Code.INTERNAL.value())
               .setDetails("server request did not match client finished frame").build());
              return resp.build();
            }
            resp.setBytesConsumed(CLIENT_FINISHED_FRAME.length());
            state = HandshakeState.COMPLETED;
          } else {
            resp.setStatus(SessionStatus.newBuilder().setCode(Code.FAILED_PRECONDITION.value())
             .setDetails("server request was not in expected state").build());
            return resp.build();
          }
        }
        resp.setStatus(SessionStatus.newBuilder().setCode(Code.OK.value()).build());
        if (state == HandshakeState.COMPLETED){
          resp.setResult(getSessionResult());
        }
      return resp.build();
      }

      //processResumption processes a resumption ticket.
      SessionResp processResumption(SessionReq req){
       return SessionResp.newBuilder().setStatus(SessionStatus.newBuilder().
        setCode(Code.OK.value()).build()).build();
      }

      private SessionResult getSessionResult(){
        SessionResult.Builder result = SessionResult.newBuilder()
         .setApplicationProtocol(GRPC_APP_PROTOCOL)
         .setState(SessionState.newBuilder().setTlsVersion(TLSVersion.TLS1_3)
          .setTlsCiphersuite(Ciphersuite.AES_128_GCM_SHA256)
          .setInKey(ByteString.copyFrom(IN_KEY.getBytes()))
          .setOutKey(ByteString.copyFrom(OUT_KEY.getBytes())).build());
        if (peerIdentity != null) {
          result.setPeerIdentity(peerIdentity);
        }
        if(localIdentity !=null) {
          result.setLocalIdentity(localIdentity);
        }
        return result.build();
      }
    };
  }
}
