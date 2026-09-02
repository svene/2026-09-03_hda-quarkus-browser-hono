package dev.svenehrke.demo.inbound.web;

/**
 * The JSON envelope returned by every {@code /uiroute/*} endpoint:
 * {@code {"route":"PersonDetails","vm":{…}}}.
 *
 * The route name travels with the data so the browser-side {@code hono} htmx
 * extension knows which template to run without an extra header or per-element
 * attribute — the URL stays the only place routes are named.
 */
public record UiResponse(String route, Object vm) {}
