package org.example;

import brave.Tracing;
import brave.context.log4j2.ThreadContextScopeDecorator;
import brave.context.slf4j.MDCScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.tracing.zipkin.HttpSenderOptions;
import io.vertx.tracing.zipkin.VertxSender;
import io.vertx.tracing.zipkin.ZipkinTracingOptions;
import zipkin2.reporter.AsyncReporter;


public class EventBusSendNotRecordedAsSpans {

    public static void main(String[] args) {
        String host = "localhost"; //zipkin server host
        int port = 9411; //zipkin server port
        boolean ssl = false;

        String serviceName = EventBusSendNotRecordedAsSpans.class.getName();

        //http://localhost:9411/api/v2/spans
        String path = String.format("%s://%s:%s/api/v2/spans", "http", host, port);
        HttpSenderOptions httpSender = new HttpSenderOptions().setSenderEndpoint(path).setDefaultHost(host).setSsl(false).setDefaultPort(port);

        VertxSender sender = new VertxSender(new HttpSenderOptions()
                .setSenderEndpoint(path)
                .setDefaultHost(host)
                .setDefaultPort(port)
                .setSsl(false));

        Vertx.clusteredVertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions(Tracing.newBuilder()
                .supportsJoin(true)
                .localServiceName(serviceName)
                .spanReporter(AsyncReporter.builder(sender).build())
                .sampler(Sampler.ALWAYS_SAMPLE)
                .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
                        .addScopeDecorator(MDCScopeDecorator.create())
                        .addScopeDecorator(ThreadContextScopeDecorator.create())
                        .build())
                .build())
                .setServiceName(serviceName)
                .setSupportsJoin(true)
                .setSenderOptions(httpSender))).onSuccess(context -> {
            /*
             * To confirm that new spans or trace are not created for messages
             * add breakpoints to {@link io.vertx.core.eventbus.impl.HandlerRegistration::next}
             * you can also check the zipkin dashboard for confirmation on http://localhost:9411
             */
            context.eventBus().consumer("test1", event -> {
                        System.out.println(event.body());
                        System.out.println(event.headers());
                    });
                    context.setPeriodic(3 * 1000, event -> context.eventBus().send("test1", new JsonObject()));
        });
    }


}
