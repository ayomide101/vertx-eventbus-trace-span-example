package org.example;

import brave.Tracing;
import brave.context.log4j2.ThreadContextScopeDecorator;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.tracing.zipkin.HttpSenderOptions;
import io.vertx.tracing.zipkin.VertxSender;
import io.vertx.tracing.zipkin.ZipkinTracingOptions;
import zipkin2.reporter.AsyncReporter;

import java.util.Random;
import java.util.concurrent.TimeUnit;


public class HttpServerEventProxyWithReplyNotRecordedAsSpans {

    /**
     * Simulates http server receiving requests and sending request to another service using eventbus, however eventbus requests not being recorded as spans in zipkin server
     *
     * @param args
     */
    public static void main(String[] args) {
        String host = "localhost"; //zipkin server host
        int port = 9411; //zipkin server port
        boolean ssl = false;

        String serviceName = HttpServerEventProxyWithReplyNotRecordedAsSpans.class.getName();

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
                    ServiceDiscovery discovery = ServiceDiscovery.create(vertx);

                    TestServiceImpl testService = new TestServiceImpl();
                    //Bind testservice to EventBus using ServiceProxyHandler
                    new ServiceBinder(vertx).setAddress(TestService.SERVICE_ADDRESS).register(TestService.class, testService);
                    //Publish service to other microservices
                    discovery.publish(EventBusService.createRecord(TestService.SERVICE_NAME, TestService.SERVICE_ADDRESS, TestService.class));

                    ///Create server
                    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS).setPort(3100))
                            .requestHandler(event -> {
                                //do event bus send when eventbus request is received
                                /*
                                 * To confirm that new spans or trace are not created for messages
                                 * add breakpoints to {@link io.vertx.core.eventbus.impl.HandlerRegistration::next}
                                 * you can also check the zipkin dashboard for confirmation on http://localhost:9411
                                 */
                                EventBusService.getProxy(discovery, TestService.class, new DeliveryOptions()
                                        .setSendTimeout(TimeUnit.MINUTES.toMillis(3))
                                        .toJson(), service -> {
                                    if (service.succeeded()) {
                                        service.result().testWithReply(new JsonObject().put("random", new Random().nextInt()), event1 -> {
                                            System.out.println(event1.result());
                                        });
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
