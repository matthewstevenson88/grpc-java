/*
 * Copyright 2015 The gRPC Authors
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

// TODO: change flags to use com.google.common.flags

/**
 * Requests a greeting from the server with TLS 1.3.
 */
public class HelloWorldClientS2A {
    private static final Logger logger = Logger.getLogger(HelloWorldClientS2A.class.getName());
    private final ManagedChannel channel;
    private GreeterGrpc.GreeterBlockingStub blockingStub;

    private static SslContext buildSslContext(String clientCertChainFilePath,
                                              String clientPrivateKeyFilePath,
                                              String trustCertCollectionFilePath) throws SSLException {
        return GrpcSslContexts.forClient()
            .trustManager(new File(trustCertCollectionFilePath))
            .keyManager(new File(clientCertChainFilePath), new File(clientPrivateKeyFilePath))
            .protocols("TLSv1.3")
            .build();
    }

    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    HelloWorldClientS2A(ManagedChannel channel) {
        this.channel = channel;
        blockingStub = GreeterGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Say hello to server.
     */
    public void greet(String name) {
        logger.info("Will try to greet " + name + " ...");
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
          logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return;
        }
        logger.info("Greeting: " + response.getMessage());
    }

    public static HelloWorldClientS2A createUsingChannel(String host, int port, SslContext sslContext) throws SSLException{
      return new HelloWorldClientS2A(NettyChannelBuilder.forAddress(host,port)
          .sslContext(sslContext)
          .build());
    }

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static void main(String[] args) throws Exception {

        String host = args[0].substring(0,args[0].indexOf(':'));
        int port = Integer.parseInt(args[0].substring(args[0].indexOf(':')+1,args[0].length()));
        String certChainFilePath = args[1];
        String privateKeyFilePath = args[2];
        String trustCertCollectionFilePath = args[3];

        HelloWorldClientS2A client= HelloWorldClientS2A.createUsingChannel(host, port,
            buildSslContext(certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath));

        try {
            client.greet(host);
        } finally {
            client.shutdown();
        }
    }
}
