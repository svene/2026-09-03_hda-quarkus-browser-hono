package dev.svenehrke.demo.inbound.web;

import dev.svenehrke.demo.core.PeopleService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Component URLs — a separate concept from the REST-ish mutation endpoints in
 * {@link PersonActionResource}. Every GET route is keyed by
 * {@link JTSPersonRouteName}; most are dispatched through the generic
 * "/uiroute/{name}" endpoint, while routes needing parameters beyond
 * {@code id} get their own dedicated {@code @Path} method (e.g. {@link #personTable}).
 *
 * <p>The response body is a JSON {@link UiResponse} envelope — the browser-side
 * {@code hono} htmx extension runs the matching hono template on {@code vm} to
 * produce the HTML fragment. No HTML is rendered on the server.
 */
@Path("/uiroute")
public class PersonUIResource {

	@Inject
	PeopleService peopleService;

	/**
	 * Handles every uiroute whose vm only ever depends on an (optional) {@code id}. A route
	 * needing different or additional parameters — like {@link #personTable} below — gets its
	 * own dedicated {@code @Path} method instead of growing this method's signature; JAX-RS
	 * matches the literal path first, so the two coexist without ambiguity.
	 */
	@GET
	@Path("/{name}") // Java-HONO
	@Produces(MediaType.APPLICATION_JSON)
	public UiResponse uiroute(@PathParam("name") String name, @QueryParam("id") Integer id) {
		JTSPersonRouteName route;
		try {
			route = JTSPersonRouteName.valueOf(name);
		} catch (IllegalArgumentException e) {
			throw new NotFoundException("Unknown uiroute: " + name);
		}
		Object vm = switch (route) {
			case Page -> new PersonPageModel(peopleService.personTableModel());
			case PersonDetails, PersonDetailsCard, PersonDetailsRow -> peopleService.personDetailModel(id);
			case PersonRow -> peopleService.personTableRowModel(id);
			case PersonEditor -> peopleService.personEditModel(id);
			default -> throw new IllegalStateException(route + " is served by its own dedicated endpoint, not " + getClass().getSimpleName() + "#uiroute");
		};
		return new UiResponse(route.name(), vm);
	}

	@GET
	@Path("/PersonTable") // Java-HONO
	@Produces(MediaType.APPLICATION_JSON)
	public UiResponse personTable(@QueryParam("search") String search) {
		return new UiResponse(JTSPersonRouteName.PersonTable.name(), peopleService.peopleForSearch(search));
	}
}
