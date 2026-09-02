# Information for Developers

currently this is WIP. More notes written down than real documentation

## Internal architecture notes

### Generate TS from Java (VM types)

- View-model types (`PersonPageModel`, `PersonTableModel`, `PersonDetailModel`, ...) are hand-written
  Java records under `src/main/java/dev/svenehrke/demo/inbound/web/`, and are the single source of truth.
  - `PersonUIResource` creates these Java records and returns them wrapped in a
    `UiResponse(String route, Object vm)` envelope; JAX-RS + jsonb serialize that to
    `application/json`. The browser `JSON.parse`s the envelope and runs the matching hono template on
    `vm` (see "Browser bundle" below). The rendering used to happen server-side inside a GraalVM
    `Context`; only the transport changed — the VM shapes the templates consume are identical.
- The `cz.habarta.typescript-generator` Maven plugin (bound to the `process-classes` phase in `pom.xml`)
  scans for classes matching `dev.svenehrke.demo.inbound.**.*Model` and `dev.svenehrke.demo.inbound.**.*VM`
  and generates matching TypeScript interfaces into\
  `src/main/java/dev/svenehrke/demo/inbound/web/generated/types/vm-types.d.ts`
  (gitignored — regenerated on every build that reaches the `process-classes` phase, e.g. `mvn package`
  or `mvn quarkus:dev`; plain `mvn compile` stops one phase too early and won't trigger it).
- `.ts` components import the generated types directly, e.g.\
  `import {PersonDetailModel} from "./generated/types/vm-types";`
- The same plugin also generates a TS union type from each of two enums, listed in the plugin's
  `<classes>` in `pom.xml` (`mapEnum: asUnion`):
  - `JTSPersonRouteName` — every component/uiroute name (see "Component URLs (uiroute)" below):
    `export type JTSPersonRouteName = "Page" | "PersonDetails" | ...`. Single source of truth for the
    route-name strings that connect `PersonUIResource.java`
    (`new UiResponse(JTSPersonRouteName.PersonDetails.name(), vm)`) to `routes.ts`'s `personRoutes`
    map — a typo or a route removed on the Java side is a TypeScript error instead of a runtime
    "ROUTE NOT FOUND" fallback.
  - `JTSPersonEventName` — every htmx/hyperscript event name used to coordinate the UI
    (`export type JTSPersonEventName = "PERSON_UPDATED" | "PersonDetailsRow_CloseCmd"`). `.ts`
    components never write an event name as a bare string — they call `jtsperson.ts`'s
    `eventName('...')` guard, which typechecks the literal against this union. `PERSON_UPDATED` is
    also the event `PersonActionResource.java` fires in its `HX-Trigger` header
    (`JTSPersonEventName.PERSON_UPDATED.name()`), so the Java and TS sides can't drift on the name.
    Enum members are `UPPER_SNAKE` (fired by Java) or `Word_Word` (`// TS-only`); both are
    hyperscript-`send`-safe unquoted, unlike a hyphenated name.
- `HonoWebApiSharedConsts.java` (`PERSON = "/person/{id}"`, `DELETE = "/delete"`) is the source of
  truth for the two mutation endpoint path templates — used both in `PersonActionResource.java`'s
  `@Path(...)` (they're compile-time constants) and, on the frontend, in `routes.ts`'s
  `personActionUrls`.
  - typescript-generator can't produce these: it only emits *types*, and the TS side needs the
    template strings as runtime values (`HonoWebApiConsts.PERSON.replace('{id}', id)`). So instead a
    small inline Groovy script in `pom.xml` (`gmavenplus-plugin`, bound to `process-classes` like
    typescript-generator) reflects over `HonoWebApiSharedConsts.HonoWebApiConsts` and writes
    `generated/types/web-api-consts.ts` (`export const HonoWebApiConsts = { … } as const`), which
    `routes.ts` imports. Nothing is hand-synced; edit the Java constants and rebuild.

### Component URLs (uiroute) vs REST-ish mutation endpoints

- Every GET route that renders an HTML fragment and only ever needs an (optional) `id` — `Page`,
  `PersonDetails`, `PersonRow`, `PersonEditor`, `PersonDetailsCard`, `PersonDetailsRow` — is dispatched
  through a single endpoint, `PersonUIResource.uiroute()` at `@Path("/uiroute/{name}")`, keyed by
  `JTSPersonRouteName`, instead of each one getting its own `@Path`. `id` is passed as a query param
  (`/uiroute/PersonDetails?id=5`). An unknown `name` returns 404.
- A route needing different or additional parameters doesn't grow `uiroute()`'s signature — it gets its
  own dedicated `@Path` method instead. `PersonTable` is the current example: it needs `search`, not `id`,
  so it has its own `PersonUIResource.personTable()` at `@Path("/PersonTable")`. JAX-RS matches the literal
  `/uiroute/PersonTable` path before falling back to the `/uiroute/{name}` template, so the two coexist
  without ambiguity. Both still return `new UiResponse(JTSPersonRouteName.xxx.name(), vm)`, so nothing
  on the frontend (`routes.ts`) needs to change when a route moves from the generic dispatcher to its
  own method.
  - `uiroute()`'s switch only lists the routes it actually serves, falling back to `default -> throw new
    IllegalStateException(...)` for anything else. This means moving a route out to its own endpoint is
    just deleting its case — no dead branch has to be added or maintained for it. The tradeoff: the
    compiler no longer forces every `JTSPersonRouteName` value to be handled somewhere in this switch, so
    a route that's added to the enum but never wired into `uiroute()` or given its own endpoint fails at
    request time (`IllegalStateException`), not at build time — the same runtime-checked risk this file
    already documents for `render.ts`'s route lookup and for an unknown `name` in the URL.
- These are deliberately treated as a separate concept from REST resources — they're URLs for fetching a
  view model for a UI component, not for a domain resource. `routes.ts`'s `personRoutes` map builds them
  (`personRoutes.PersonDetails.url(id)`, `personRoutes.PersonEditor.url(id)`, `personRoutes.PersonTable.url()`,
  ...) from `JTSPersonRouteName` values, and pairs each URL with the render function for that route —
  see "Browser bundle (hono/html templates + the `hono` htmx extension)" below.
- Mutations (`PUT /person/{id}` to save an edit, `DELETE /delete` for bulk delete) stay on their own
  REST-ish paths in a separate class, `PersonActionResource.java` — they don't go through `/uiroute`.
  `routes.ts`'s `personActionUrls.UpdatePerson.url(id)` builds the `PUT` URL from
  `HonoWebApiConsts.PERSON`; `personActionUrls.Delete.url()` builds the `DELETE` URL from
  `HonoWebApiConsts.DELETE` (both from generated `web-api-consts.ts` — see above).
- `GET /` serves the static shell `src/main/resources/META-INF/resources/index.html` (Quarkus/Vert.x
  welcome file — there is no `RootResource`). Its `#app` div does
  `hx-get="/uiroute/Page" hx-trigger="load"` to bootstrap the first render.

### Browser bundle (hono/html templates + the `hono` htmx extension)

- The `.ts` components render HTML with hono's `html` tagged-template function
  (`import {html} from "hono/html"`), not with JSX. Each component is a plain function
  `(vm: SomeModel): HtmlResult => html`...`` where `HtmlResult = ReturnType<typeof html>`
  (see `route-types.ts`). These files used to be `.tsx` (JSX) — since the conversion they contain
  no JSX and are plain `.ts`; `tsconfig.json` no longer sets `jsx` / `jsxImportSource`.
- Rendering runs **in the browser**. `PersonUIResource` returns `{ route, vm }` as JSON; a small
  htmx 4 extension (`hono`, in `hx-hono.ts`) intercepts each `/uiroute/*` response and swaps the
  produced HTML in.
- Built with `npm run build`, which runs:\
`esbuild src/main/java/dev/svenehrke/demo/inbound/web/hx-hono.ts --bundle --platform=browser --format=iife --outfile=src/main/resources/META-INF/resources/js/hono/hx-hono.js`\
  (`npm run build:prod` adds `--minify` for release jars — see "Dev vs. production build" below).
- `hx-hono.ts` is the esbuild entry — it imports `render` and registers the extension:
````ts
import { render } from "./render";
declare const htmx: any;

htmx.registerExtension("hono", {
  // htmx 4 hard-codes `Accept: text/html`; /uiroute/* only produces JSON, so ask for it.
  htmx_config_request: (_elt, detail) => {
    detail.ctx.request.headers["Accept"] = "application/json, text/html;q=0.9";
  },
  // htmx 4's transformResponse equivalent: rewrite the body before the swap.
  htmx_after_request: (_elt, detail) => {
    const ctx = detail.ctx;
    const ct = ctx.response?.headers?.get?.("content-type") ?? "";
    if (!ct.includes("application/json")) return;   // mutations, errors → leave alone
    if (!ctx.text) return;
    const { route, vm } = JSON.parse(ctx.text);
    ctx.text = render(route, vm);
  },
});
````
- `render.ts` no longer talks to any JS engine. It takes the already-parsed `vm` object and returns
  a `string`; it doesn't dispatch itself — it looks `route` up in `routes.ts`'s `personRoutes` map:
````ts
import {html} from 'hono/html';
import {personRoutes} from "./routes";
import {RouteDefinition} from "./route-types";

export function render(route: string, vm: unknown): string {
  const defs = personRoutes as Record<string, RouteDefinition>;
  const def = defs[route];
  return String(def ? def.render(vm) : html`<div>ROUTE '${route}' NOT FOUND</div>`);
}
````
- **Why the `String(...)` at the boundary matters:** the per-route `render` functions return
  `HtmlResult` (a boxed `HtmlEscapedString`, possibly a `Promise`); `String(...)` collapses that to
  the primitive `string` the extension assigns to `ctx.text`, and runs hono's stringify phase.
  `render.ts`'s header comment has the full explanation. Rule of thumb: `HtmlResult` everywhere
  inside the components, stringify exactly once in `render.ts`.
- htmx 4 extension notes: `htmx.registerExtension()` (not `defineExtension`), underscored hook names
  (`htmx_after_request` = the `htmx:after:request` event), extensions are **global** (every hook runs
  for every request regardless of `hx-ext`), and `hx-hono.js` must load *after* `htmx.js` and not be
  deferred (it calls `registerExtension` at parse time).
- `routes.ts`'s `personRoutes` is typed as `satisfies Record<JTSPersonRouteName, RouteDefinition>` — every
  value of the generated `JTSPersonRouteName` union must have a `{url, render}` entry, or the file fails
  to typecheck. This is what actually guarantees every route has both a URL builder and a render
  function — but only if something actually typechecks `routes.ts`: `npm run build` (esbuild) does
  **not**, it only strips types and bundles, so a missing entry currently only gets caught by your
  editor's TS language server, not by the build. `render.ts`'s lookup-and-fallback is a runtime safety
  net for a route name that somehow reaches it without going through `PersonUIResource.uiroute()`'s own
  `JTSPersonRouteName.valueOf(name)` validation (which already 404s on an unknown name before `render()`
  is ever called).
- Adding a new route means: add the `JTSPersonRouteName` enum value, add its `case` in
  `PersonUIResource.uiroute()`'s Java `switch` (returning `new UiResponse(route.name(), vm)`), and
  add its entry to `personRoutes` in `routes.ts`.

#### Dev vs. production build (minification)

Two `package.json` scripts, one shared esbuild invocation:

| script | output | use |
|---|---|---|
| `npm run build` | readable, ~12 KB | dev; run by `npm run watch` and by the Playwright `webServer` |
| `npm run build:prod` | `--minify`, ~7.5 KB (~2.5 KB gzipped) | release jars — run it **before `mvn package`** |

`build:prod` is literally `npm run build -- --minify`, so the entry point / output path live in one
place. `mvn package` just copies whatever `hx-hono.js` is currently on disk into
`target/classes/META-INF/resources/` — nothing in the Maven build regenerates it, so a release flow
is:

```
npm run build:prod && mvn package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

Notes:

- esbuild `--minify` does whitespace removal + identifier renaming + syntax compression. It does
  **not** rename object-literal keys or string literals, so the name-sensitive parts survive:
  `registerExtension("hono", …)` and the hook keys `htmx_config_request` / `htmx_after_request`
  (htmx looks those up by name).
- No source map is emitted. Add `--sourcemap=external` to `build:prod` if you want one (it writes a
  git-ignored `hx-hono.js.map` alongside).
- Minification and HTTP compression are complementary. To let Quarkus gzip/brotli-compress the
  served bundle, set `quarkus.http.enable-compression=true` (optionally
  `quarkus.http.compress-media-types=application/javascript,text/javascript,...`).
- Wiring `build:prod` into `mvn package` (via `frontend-maven-plugin` or `exec-maven-plugin`) is a
  possible follow-up — deliberately not done, to keep the frontend build a plain `npm` concern.

#### `.tsx` -> `.ts` (done)

The web layer used JSX until the `hono/html` conversion; afterwards no file under
`src/main/java/dev/svenehrke/demo/inbound/web/` contained JSX, so all of them were renamed `.tsx` ->
`.ts`. Changed at the same time: `package.json` `build` script (`render.ts`), `watch.ts` (now
filters `.endsWith(".ts")`, so it also rebuilds on plain `.ts` edits like `route-types.ts`, which it
ignored before), and `tsconfig.json` (dropped the now-dead `jsx` / `jsxImportSource` options).

### Live reload for the browser
During development the browser should automatically refresh when one of the `.ts` files is changed.

This is achieved by using an SSE connection (see `DevReloadSSE.java`, `inbound/web/infra/`) which is
triggered by `JsBundleWatcher` whenever the emitted `hx-hono.js` bundle changes (it polls
`src/main/resources/META-INF/resources/js/hono/hx-hono.js`'s `lastModified` once a second). There is
no server-side JS engine to re-initialise anymore — a changed bundle just broadcasts a reload.

`index.html` loads `dev.js`, which listens to those SSE events:
````js
new EventSource("/dev-reload")
  .addEventListener("reload", () => {
    console.log("Reload triggered");
    location.reload();
    }
  );
````

Browsers HTTP-cache `hx-hono.js` aggressively — the SSE reload picks up the rebuilt bundle, but a
manual refresh may need a hard reload (Ctrl+Shift+R).

### End-to-end tests

- `playwright/` is a separate npm project (own `package.json`/`node_modules`, not the frontend one at
  the repo root) containing a Playwright test suite (`playwright/tests/main.spec.ts`).
- Run it with `npm test` from inside `playwright/` (or `npx playwright test`).
- `playwright.config.ts`'s `webServer` builds the browser bundle (`npm run build`), packages the app
  on the default JDK (plain `mvn package -q -DskipTests` — no GraalVM), and starts a fresh instance on
  port 8080 before every run (`java -jar target/quarkus-app/quarkus-run.jar`) — the in-memory H2
  database is always re-seeded from scratch (`DBInitializer`, `Faker` with seed `0`), so the tests can
  rely on deterministic data (e.g. the first seeded person is always "Jackie Rau").
- The tests load the static shell at `/` (a `gotoApp(page)` helper waits for the load-triggered
  `#result-table table` render) and then exercise the actual app routes: `/uiroute/{name}` component
  URLs (`/uiroute/Page`, `/uiroute/PersonDetails?id=..`, ...) — which now return the `{ route, vm }`
  JSON envelope, rendered client-side — plus the separate `PUT /person/{id}` and `DELETE /delete`
  mutation endpoints (`DELETE` redirects to `/`).


