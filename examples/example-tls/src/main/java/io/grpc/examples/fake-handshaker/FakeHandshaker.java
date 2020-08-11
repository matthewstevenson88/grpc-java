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
import com.google.net.grpc.s2a.handshaker.S2AServiceGrpc;
import com.google.net.grpc.s2a.handshaker.SessionReq;
import com.google.net.grpc.s2a.handshaker.SessionResp;


import io.grpc.Server;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

// TODO: change flags to use com.google.common.flags

/**
 * Server that manages startup/shutdown of a Greeter  server with TLS 1.3 enabled.
 */
public class FakeHandshaker {
    private static final Logger logger = Logger.getLogger(FakeHandshaker.class.getName());
    private Server server;
    private final int port;

    private FakeHandshaker(int port) {
        this.port = port;
    }

    private void start() throws IOException {
        server = NettyServerBuilder.forPort(port)
                .addService(new HandshakerImpl())
                .build()
                .start();

        logger.info("Server started, listening at address " + "localhost:"+ port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("shutting down gRPC server since JVM is shutting down");
                FakeHandshaker.this.stop();
                System.err.println("server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Awaits termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Starts a fake handshaker service.
     *
     * args:
     * @param arg[0] contains the server port
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        String[] defaultArgs = new String[]{
          "50051"
        };

        // Checks if the args are missing information.
        if (args.length != defaultArgs.length){
          args = defaultArgs;
        }

        FakeHandshaker server =  new FakeHandshaker(Integer.parseInt(args[0]));
        server.start();
        server.blockUntilShutdown();
    }

    private static class HandshakerImpl extends S2AServiceGrpc.S2AServiceImplBase {
        @Override
        public StreamObserver<SessionReq> setUpSession(StreamObserver<SessionResp> req) {
            return null;
        }
    }


}
