package dev.svenehrke.demo.inbound.web.infra;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dev-only: polls the emitted browser bundle and pushes a reload to the browser
 * when it changes. No server-side JS engine to re-init anymore — the templates
 * run in the browser — so a changed bundle only means "tell the page to reload".
 */
@IfBuildProfile("dev")
@ApplicationScoped
@Startup
public class JsBundleWatcher {

	private static final Logger log = LoggerFactory.getLogger(JsBundleWatcher.class);

	/** The esbuild output watched by {@code npm run watch}; path is relative to the dev working dir. */
	private static final Path BUNDLE = Path.of("src/main/resources/META-INF/resources/js/hono/hx-hono.js");

	@Inject
	DevReloadSSE devReloadSSE;

	private long lastModified = -1;

	@Scheduled(every = "1s")
	void checkFile() throws Exception {
		if (!Files.exists(BUNDLE)) {
			return;
		}

		long current = Files.getLastModifiedTime(BUNDLE).toMillis();

		if (current != lastModified) {
			lastModified = current;
			log.info("browser bundle changed → broadcasting reload");
			devReloadSSE.broadcastReload();
		}
	}
}
