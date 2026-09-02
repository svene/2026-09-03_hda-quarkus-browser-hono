import {render} from "./render";

declare const htmx: any;

/**
 * The `hono` htmx 4 extension: renders the hono `html` templates in the browser.
 *
 * `/uiroute/*` endpoints return a JSON envelope `{ route, vm }` instead of an
 * HTML fragment. This hook intercepts the response before the swap, runs the
 * matching hono template on `vm`, and replaces `ctx.text` with the produced
 * HTML string so the normal htmx swap proceeds unchanged.
 *
 * `htmx_after_request` is htmx 4's sanctioned place to rewrite the response body
 * before the swap (htmx 2's `transformResponse`). Mutations (`PUT /person/{id}`,
 * `DELETE /delete`) and error responses are not `application/json` fragments and
 * are left untouched.
 */
htmx.registerExtension("hono", {
	// htmx 4 hard-codes `Accept: text/html`; the `/uiroute/*` endpoints only
	// produce the JSON envelope, so ask for JSON (HTML kept as a fallback so
	// error pages and the HX-Redirect target still negotiate normally).
	htmx_config_request: (_elt: Element, detail: any) => {
		detail.ctx.request.headers["Accept"] = "application/json, text/html;q=0.9";
	},

	htmx_after_request: (_elt: Element, detail: any) => {
		const ctx = detail.ctx;
		const contentType = ctx.response?.headers?.get?.("content-type") ?? "";
		if (!contentType.includes("application/json")) return; // mutations, errors → leave alone
		if (!ctx.text) return;
		const {route, vm} = JSON.parse(ctx.text);
		ctx.text = render(route, vm); // hono html`…` runs here
	},
});
