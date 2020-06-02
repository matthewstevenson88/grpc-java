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
public class HelloWorldServerS2A {
    private static final Logger logger = Logger.getLogger(HelloWorldServerS2A.class.getName());
    private Server server;
    private final int port;
    private final String certChainFilePath;
    private final String privateKeyFilePath;
    private final String trustCertCollectionFilePath;

    private HelloWorldServerS2A(int port,
                             String certChainFilePath,
                             String privateKeyFilePath,
                             String trustCertCollectionFilePath) {
        this.port = port;
        this.certChainFilePath = certChainFilePath;
        this.privateKeyFilePath = privateKeyFilePath;
        this.trustCertCollectionFilePath = trustCertCollectionFilePath;
    }

    private SslContextBuilder getSslContextBuilder() {
      SslContextBuilder sslClientContextBuilder = SslContextBuilder.forServer(new File(certChainFilePath), new File(privateKeyFilePath));
        sslClientContextBuilder.trustManager(new File(trustCertCollectionFilePath));
        sslClientContextBuilder.clientAuth(ClientAuth.REQUIRE);
        sslClientContextBuilder.protocols("TLSv1.3");
        return GrpcSslContexts.configure(sslClientContextBuilder);
    }

    private void start() throws IOException {
        server = NettyServerBuilder.forPort(port)
                .sslContext(getSslContextBuilder().build())
                .addService(new GreeterImpl())
                .build()
                .start();

        logger.info("Server started, listening at address " + "localhost:"+ port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("shutting down gRPC server since JVM is shutting down");
                HelloWorldServerS2A.this.stop();
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

    private static HelloWorldServerS2A create(int port,
                             String certChainFilePath,
                             String privateKeyFilePath,
                             String trustCertCollectionFilePath) {
        checkNotNull(certChainFilePath, "certChainFilePath should not be Null");
        checkNotNull(privateKeyFilePath, "privateKeyFilePath should not be Null");
        checkNotNull(trustCertCollectionFilePath, "trustCertCollectionFilePath should not be Null");
        return new HelloWorldServerS2A(port,
            certChainFilePath,
            privateKeyFilePath,
            trustCertCollectionFilePath);
    }

    /**
     * Stars a greeter service
     *
     * args:
     * @param arg[0] contains the server port
     * @param arg[1] contains the file path to the server certificate
     * @param arg[2] contains the file path to the server private key
     * @param arg[3] contains the file path to the CA certificate
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        String[] defaultArgs = new String[]{
          "50051",
          "testdata/service.pem",
          "testdata/service.key",
          "testdata/ca.pem"};

        //Check if the args are missing information
        if (args.length != defaultArgs.length){
          System.out.println("Arguments invalid; Using default arguments");
          args = defaultArgs;
        }

        HelloWorldServerS2A server = HelloWorldServerS2A.create(
            Integer.parseInt(args[0]), args[1], args[2], args[3]);
        server.start();
        server.blockUntilShutdown();
    }

    private static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
        @Override
        public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
