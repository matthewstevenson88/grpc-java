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

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;
import javax.annotation.Nonnull;

// TODO: change flags to use com.google.common.flags

/**
 * Establishes a TLS 1.3 connection with a greeter service.
 */
public class HelloWorldClientS2A {
    private static final Logger logger = Logger.getLogger(HelloWorldClientS2A.class.getName());
    private final ManagedChannel channel;
    private GreeterGrpc.GreeterBlockingStub blockingStub;

    private static SslContext buildSslContext(String clientCertChainFilePath,
                                              String clientPrivateKeyFilePath,
                                              String trustCertCollectionFilePath) throws SSLException {
      checkNotNull(clientCertChainFilePath, "clientCertChainFilePath should not be Null");
      checkNotNull(clientPrivateKeyFilePath, "clientPrivateKeyFilePath should not be Null");
      checkNotNull(trustCertCollectionFilePath, " trustCertCollectionFilePath should not be Null");
      return GrpcSslContexts.forClient()
            .trustManager(new File(trustCertCollectionFilePath))
            .keyManager(new File(clientCertChainFilePath), new File(clientPrivateKeyFilePath))
            .protocols("TLSv1.3")
            .build();
    }

    /**
     * Constructs client for accessing greeter server using the existing channel.
     */
    private HelloWorldClientS2A(ManagedChannel channel, GreeterGrpc.GreeterBlockingStub blockingStub) {
        this.channel = channel;
        this.blockingStub = blockingStub;
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Says hello to a greeter server.
     */
    private void greet(String name) {
        checkNotNull(name, "name should not be Null");
        logger.info("Will try to greet " + name + " ...");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response = blockingStub.sayHello(request);
        logger.info("Greeting: " + response.getMessage());
    }

    private static HelloWorldClientS2A create(String host, int port, SslContext sslContext) throws SSLException {
        checkNotNull(host, "host should not be Null");
        checkNotNull(sslContext, "SslContext should not be Null");
        ManagedChannel channel = NettyChannelBuilder.forAddress(host,port)
                        .sslContext(sslContext)
                        .build();
        return new HelloWorldClientS2A(channel, GreeterGrpc.newBlockingStub(channel));
    }

    /**
     * Sends a {@code HelloRequest} to a greeter service.
     *
     * args:
     * @param arg[0] contains the server address in the form of host:port
     * @param arg[1] contains the file path to the client certificate
     * @param arg[2] contains the file path to the client's private key
     * @param arg[3] contains the file path to the CA certificate
     */
    public static void main(String[] args) throws SSLException, InterruptedException {
        String[] defaultArgs = new String[] {
          "localhost:50051",
          "testdata/client.pem",
          "testdata/client.key",
          "testdata/ca.pem"};

        // Checks if the args are missing information.
        if (args.length != defaultArgs.length){
            logger.info("Arguments invalid; Using default arguments");
            args = defaultArgs;
        }

        HelloWorldClientS2A client= HelloWorldClientS2A.create(
                args[0].substring(0,args[0].indexOf(':')),
                Integer.parseInt(args[0].substring(args[0].indexOf(':')+1,args[0].length())),
                buildSslContext(args[1], args[2], args[3]));;
        try {
            client.greet(args[0].substring(0,args[0].indexOf(':')));
        } finally {
            client.shutdown();
        }
    }
}
