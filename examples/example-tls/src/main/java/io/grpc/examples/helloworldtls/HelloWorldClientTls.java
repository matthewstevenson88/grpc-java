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
/*
import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.common.flags.Flags;
*/
// TODO: change flags to use com.google.common.flags


/**
 * A simple client that requests a greeting from the {@link HelloWorldServerTls} with TLS.
 */
public class HelloWorldClientTls {
    private static final Logger logger = Logger.getLogger(HelloWorldClientTls.class.getName());

    private final ManagedChannel channel;
    private GreeterGrpc.GreeterBlockingStub blockingStub;

    /*
    @FlagSpec(name = "server_address", help = "address of the server")
    private static final Flag<String> serverAddr = "localhost:50051";
    @FlagSpec(name = "server_root_cert_pem_path", help = "path to the root X509 certificate")
    private static final Flag<String> rootCert = "example-tls/testdata/ca.cert";
    @FlagSpec(name = "client_cert_pem_path", help = "path to client's X509 certificate")
    private static final Flag<String> certFile = "example-tls/testdata/client.server";
    @FlagSpec(name = "client_key_pem_path", help = "path to client's private key")
    private static final Flag<String> keyFile = "example-tls/testdata/client.key";
    */

    private static SslContext buildSslContext(String clientCertChainFilePath,
                                              String clientPrivateKeyFilePath,
                                              String trustCertCollectionFilePath) throws SSLException {
        SslContextBuilder builder = GrpcSslContexts.forClient();
        builder.trustManager(new File(trustCertCollectionFilePath));
        builder.keyManager(new File(clientCertChainFilePath), new File(clientPrivateKeyFilePath));
        //builder.protocols(new String[]{"1.3"});
        SslContext context = builder.build();
        System.out.println(context.nextProtocols());
        return context;
    }

    /**
     * Construct client connecting to HelloWorld server at {@code host:port}.
     */
    public HelloWorldClientTls(String host,
                               int port,
                               SslContext sslContext) throws SSLException {

        this(NettyChannelBuilder.forAddress(host, port)
            .sslContext(sslContext)
            .build());
        //blockingStub = GreeterGrpc.newBlockingStub(channel);
    }

    /**
     * Construct client for accessing RouteGuide server using the existing channel.
     */
    HelloWorldClientTls(ManagedChannel channel) {
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

    /**
     * Greet server. If provided, the first element of {@code args} is the name to use in the
     * greeting.
     */
    public static void main(String[] args) throws Exception {
        // ./build/install/example-tls/bin/hello-world/tls/client localhost:50051 /testdata/client.pem testdata/client.key testdata/ca.pem
        //String[] args = Flags.parseAndReturnLeftovers(args);

        String host = args[0].substring(0,args[0].indexOf(':'));
        int port = Integer.parseInt(args[0].substring(args[0].indexOf(':')+1,args[0].length()));
        String certChainFilePath = args[1];
        String privateKeyFilePath = args[2];
        String trustCertCollectionFilePath = args[3];

        HelloWorldClientTls client= new HelloWorldClientTls(host, port,
            buildSslContext(certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath));

        try {
            client.greet(host);
        } finally {
            client.shutdown();
        }
    }
}
