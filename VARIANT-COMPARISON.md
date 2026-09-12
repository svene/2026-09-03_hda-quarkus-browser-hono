# Variant note

This repo is the **browser-rendering** fork of
[`2026-03-15_hypermedia-quarkus-graalvm-hono-demo`](../2026-03-15_hypermedia-quarkus-graalvm-hono-demo).

Same app, same hono/html templates, same htmx/hyperscript choreography, same Java→TS codegen, same
persistence. The only difference is *where the templates run*:

| | GraalVM demo (upstream) | this repo |
|---|---|---|
| `/uiroute/*` response | `text/html` fragment | `{ "route", "vm" }` JSON envelope |
| Template execution | server-side, GraalVM `Context` pool | browser, via the `hono` htmx extension (`hx-hono.js`) |
| First paint | `GET /` → 303 → `/uiroute/Page` (SSR) | static `index.html` shell, `#app` self-bootstraps with `hx-trigger="load"` |
| Runtime | GraalVM JDK (for JS JIT) | plain JDK 21 |
| Deps | `org.graalvm.polyglot:{polyglot,js}` | none — rendering moved to the browser |

See `architecture.md` for the full "after" picture.

*(The three-way comparison with the Spring Boot SSR twin lives in the upstream repo's
`VARIANT-COMPARISON.md`.)*
