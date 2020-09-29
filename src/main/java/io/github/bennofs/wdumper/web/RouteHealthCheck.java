package io.github.bennofs.wdumper.web;

import ratpack.exec.Promise;
import ratpack.health.HealthCheck;
import ratpack.http.client.HttpClient;
import ratpack.registry.Registry;
import ratpack.server.PublicAddress;

public class RouteHealthCheck implements HealthCheck {
    private final String route;

    public RouteHealthCheck(String route) {
        this.route = route;
    }


    @Override
    public String getName() {
        return "route/" + route;
    }

    @Override
    public Promise<Result> check(Registry registry) throws Exception {
        final HttpClient client = registry.get(HttpClient.class);
        final PublicAddress address = registry.get(PublicAddress.class);

        return client.get(address.get(route)).map(resp -> {
            if (resp.getStatus().is2xx()) {
                return Result.healthy();
            }
            return Result.unhealthy("status is not 2xx: " + resp.getStatus().toString());
        });
    }
}
