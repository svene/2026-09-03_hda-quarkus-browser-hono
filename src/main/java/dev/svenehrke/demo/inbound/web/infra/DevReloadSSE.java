package dev.svenehrke.demo.inbound.web.infra;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.vertx.web.Route;
import io.vertx.core.http.HttpServerResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@IfBuildProfile("dev")
@ApplicationScoped
public class DevReloadSSE {

	private final Set<HttpServerResponse> clients = ConcurrentHashMap.newKeySet();

	@Route(path = "/dev-reload", methods = Route.HttpMethod.GET)
	void stream(io.vertx.ext.web.RoutingContext ctx) {
		HttpServerResponse response = ctx.response();

		response.putHeader("Content-Type", "text/event-stream");
		response.putHeader("Cache-Control", "no-cache");
		response.putHeader("Connection", "keep-alive");
		response.setChunked(true);

		clients.add(response);

		response.closeHandler(v -> clients.remove(response));

		// initial ping (optional)
		response.write("event: connected\ndata: ok\n\n");
	}

	public void broadcastReload() {
		clients.forEach(resp -> {
			try {
				resp.write("event: reload\ndata: now\n\n");
			} catch (Exception e) {
				clients.remove(resp);
			}
		});
	}
}
