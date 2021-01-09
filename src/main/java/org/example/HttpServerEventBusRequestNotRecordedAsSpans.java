package org.example;

import brave.Tracing;
import brave.context.log4j2.ThreadContextScopeDecorator;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.*;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.tracing.zipkin.HttpSenderOptions;
import io.vertx.tracing.zipkin.VertxSender;
import io.vertx.tracing.zipkin.ZipkinTracingOptions;
import zipkin2.reporter.AsyncReporter;

import java.util.Random;


public class HttpServerEventBusRequestNotRecordedAsSpans {

    /**
     * Simulates http server receiving requests and sending request to another service using eventbus, however eventbus requests not being recorded as spans in zipkin server
     *
     * @param args
     */
    public static void main(String[] args) {
        String host = "localhost"; //zipkin server host
        int port = 9411; //zipkin server port
        boolean ssl = false;

        String serviceName = HttpServerEventBusRequestNotRecordedAsSpans.class.getName();

        //http://localhost:9411/api/v2/spans
        String path = String.format("%s://%s:%s/api/v2/spans", "http", host, port);
        HttpSenderOptions httpSender = new HttpSenderOptions().setSenderEndpoint(path).setDefaultHost(host).setSsl(false).setDefaultPort(port);

        Vertx.clusteredVertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions(Tracing.newBuilder()
                .supportsJoin(true)
                .localServiceName(serviceName)
                .spanReporter(AsyncReporter.builder(new VertxSender(httpSender)).build())
                .sampler(Sampler.ALWAYS_SAMPLE)
                .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
                        .addScopeDecorator(MDCScopeDecorator.create())
                        .addScopeDecorator(ThreadContextScopeDecorator.create())
                        .build())
                .build())
                .setServiceName(serviceName)
                .setSupportsJoin(true)
                .setSenderOptions(httpSender)))
                .onSuccess(vertx -> {
                    //Simulate listener
                    vertx.eventBus().consumer("test1", event -> {
                        System.out.println(event.body());
                        System.out.println(event.headers());
                        event.reply(event.body());
                    });
                    ///Create server
                    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS).setPort(3100))
                            .requestHandler(event -> {
                                System.out.printf("received request %s%n", event.body());
                                //do event bus send when eventbus request is received
                                /*
                                 * To confirm that new spans or trace are not created for messages
                                 * add breakpoints to {@link io.vertx.core.eventbus.impl.HandlerRegistration::next}
                                 * you can also check the zipkin dashboard for confirmation on http://localhost:9411
                                 */
                                vertx.eventBus().request("test1", new Random().nextInt(), event1 -> {
                                    if (event1.succeeded()) {
                                        System.out.println(event1.result().body());
                                    }
                                });
                            })
                            .listen()
                            .onSuccess(event -> {
                                System.out.printf("HTTPServer started on %s%n", event.actualPort());
                                //Create http client to send request to server
                                HttpClient client = vertx.createHttpClient(new HttpClientOptions().setConnectTimeout(1000).setDefaultHost("localhost").setDefaultPort(3100));
                                vertx.setPeriodic(3 * 1000, event1 -> client.request(new RequestOptions().setPort(3100).setHost("localhost").setURI("/"))
                                        .onSuccess(HttpClientRequest::send));
                            });
                });
    }


}
