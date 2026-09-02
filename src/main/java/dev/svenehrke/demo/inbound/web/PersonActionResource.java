package dev.svenehrke.demo.inbound.web;

import dev.svenehrke.demo.core.PeopleService;
import dev.svenehrke.demo.inbound.web.form.PersonEditForm;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST-ish mutation endpoints — a separate concept from the component URLs
 * dispatched through {@link PersonUIResource}.
 */
@Path("/")
public class PersonActionResource {

	@Inject
	PeopleService peopleService;

	@PUT
	@Path(HonoWebApiSharedConsts.HonoWebApiConsts.PERSON) // Java-HONO
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	public Response updatePerson(@PathParam("id") int id, @BeanParam PersonEditForm personEditForm) {
		peopleService.updatePerson(id, personEditForm.toModel(id));
		return Response
			.ok()
			.header(
				HTMXConsts.HX_TRIGGER,
				"""
					{"%s": {"id": %d}}\
					""".formatted(JTSPersonEventName.PERSON_UPDATED.name(), id)
			)
			.build();
	}

	@DELETE
	@Path(HonoWebApiSharedConsts.HonoWebApiConsts.DELETE) // Java-HONO
	public Response deleteRows(@QueryParam("selection") List<Integer> selection) {
		peopleService.deleteByIds(selection);
		return Response
			.ok()
			.header(HTMXConsts.HX_REDIRECT, "/")
			.build();
	}

}
