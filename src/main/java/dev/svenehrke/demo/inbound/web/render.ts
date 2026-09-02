import {html} from 'hono/html';
import {personRoutes} from "./routes";
import {RouteDefinition} from "./route-types";

/**
 * Dispatches a route name + its view model to the matching hono template and
 * returns the produced HTML as a primitive `string`.
 *
 * Stringify happens exactly once, here at the boundary: the individual route
 * `render` functions return `HtmlResult` (`html`...`` yields an
 * `HtmlEscapedString`, i.e. a boxed String, possibly wrapped in a Promise).
 * `String(...)` collapses that to the primitive string the `hono` extension
 * hands to htmx, and runs hono's stringify phase that resolves any deferred
 * escaping callbacks. Components stay in `HtmlResult`; the extension gets a
 * `string`.
 */
export function render(route: string, vm: unknown): string {
	const routeDefinitions = personRoutes as Record<string, RouteDefinition>;
	const routeDefinition = routeDefinitions[route];
	return String(routeDefinition
		? routeDefinition.render(vm)
		: html`<div>ROUTE '${route}' NOT FOUND</div>`);
}
