package org.example;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.Date;

public class TestServiceImpl implements TestService{
    @Override
    public TestService testWithReply(JsonObject data, Handler<AsyncResult<JsonObject>> handler) {
        System.out.printf("message received: %s%n", data);
        handler.handle(Future.succeededFuture(data.put("reply", new Date().toString())));
        return this;
    }

    @Override
    public TestService testWithoutReply(JsonObject data) {
        System.out.printf("message received: %s%n", data);
        return this;
    }
}
