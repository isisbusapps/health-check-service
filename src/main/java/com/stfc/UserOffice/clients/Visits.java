package com.stfc.UserOffice.clients;

import com.stfc.UserOffice.dto.Status;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestResponse;

@Path(("quarkus/health"))
@RegisterRestClient
public interface Visits {
    @GET
    RestResponse<Status> checkVisits();
}
