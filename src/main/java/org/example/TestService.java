package org.example;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

@ProxyGen
@VertxGen
public interface TestService {

    /**
     * The service address for eventbus communication
     */
    String SERVICE_ADDRESS = TestService.class.getCanonicalName();

    /**
     * The service name for which other services can search by
     */
    String SERVICE_NAME = TestService.class.getCanonicalName();

    @Fluent
    TestService testWithReply(JsonObject data, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    TestService testWithoutReply(JsonObject data);
}