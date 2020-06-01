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

/*
import com.google.common.flags.Flag;
import com.google.common.flags.FlagSpec;
import com.google.common.flags.Flags;
*/

// TODO: change flags to use com.google.common.flags


/**
 * Server that manages startup/shutdown of a {@code Greeter} server with TLS enabled.
 */
public class HelloWorldServerTls {
    private static final Logger logger = Logger.getLogger(HelloWorldServerTls.class.getName());

    private Server server;

    private final int port;
    private final String certChainFilePath;
    private final String privateKeyFilePath;
    private final String trustCertCollectionFilePath;

    /*
    @FlagSpec(name = "port", help = "port number to use for connection")
    private static final Flag<String> portFlag = "50051";
    @FlagSpec(name = "client_root_cert_pem_path", help = "path to root X509 certificate")
    private static final Flag<String> rootCert = "example-tls/testdata/ca.cert";
    @FlagSpec(name = "server_cert_pem_path", help = "path to server's X509 certificate")
    private static final Flag<String> certFile = "example-tls/testdata/service.pem";
    @FlagSpec(name = "server_key_pem_path", help = "path to server's private key")
    private static final Flag<String> keyFile = "example-tls/testdata/service.key";
    */

  public HelloWorldServerTls(int port,
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
        if (trustCertCollectionFilePath != null) {
            sslClientContextBuilder.trustManager(new File(trustCertCollectionFilePath));
            sslClientContextBuilder.clientAuth(ClientAuth.REQUIRE);
            sslClientContextBuilder.protocols(new String[] {"TLSv1.3"});
        }
        return GrpcSslContexts.configure(sslClientContextBuilder);
    }

    private void start() throws IOException {
       server = NettyServerBuilder.forPort(port)
                //.useTransportSecurity(new File(certChainFilePath), new File(privateKeyFilePath))
                .sslContext(getSslContextBuilder().build())
                .addService(new GreeterImpl())
               .build()
               .start();

        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                HelloWorldServerTls.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        // ./build/install/example-tls/bin/hello-world-tls-server 50051 testdata/service.pem testdata/service.key testdata/ca.pem
        //String[] args = Flags.parseAndReturnLeftovers(args);

        int port = Integer.parseInt(args[0]);
        String certChainFilePath = args[1];
        String privateKeyFilePath = args[2];
        String trustCertCollectionFilePath = args[3];

        final HelloWorldServerTls server = new HelloWorldServerTls(
            port, certChainFilePath, privateKeyFilePath, trustCertCollectionFilePath);
        server.start();
        server.blockUntilShutdown();
    }

    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {

        @Override
        public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }
}
