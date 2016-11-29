package org.marble.processor.simple.controller;

import java.io.InputStream;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.glassfish.jersey.media.multipart.FormDataParam;
import org.marble.model.model.ProcessorInput;
import org.marble.model.model.ProcessorOutput;
import org.marble.processor.simple.service.ProcessorService;
import org.marble.processor.simple.service.SenticNetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Component
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "Processing Resource", produces = "application/json")
public class ProcessingResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingResource.class);

    @Autowired
    SenticNetService senticNetService;
    
    @Autowired
    ProcessorService processorService;
    
    @POST
    @Path("process")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Processes a message and returns its polarity using the simple processor.", response = ProcessorOutput.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Message processed.")
    })
    public Response process(ProcessorInput input, @Context UriInfo uriInfo) {
        LOGGER.info("Received <process> request with input <" + input + ">");
        ProcessorOutput output = new ProcessorOutput();
        output.setPolarity(processorService.processMessage(input.getMessage(), input.getOptions()));
        output.setNotes("Processed message <"+input.getMessage()+"> using simple processor.");
        LOGGER.info("Sent <process> response with output <" + output + ">");
        return Response.status(Status.OK).entity(output).build();
    }
    
    @POST
    @Path("update_senticnet")
    @Consumes(MediaType.MULTIPART_FORM_DATA) 
    @ApiOperation(value = "Processes a message and returns its polarity using the simple processor.", response = ProcessorOutput.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Message processed.")
    })
    public Response uploadSenticNet(@FormDataParam("file") InputStream inputStream) {
        try {
            senticNetService.insertDataFromFile(inputStream);
            return Response.status(Status.OK).build();
        } catch (Exception e) {
            LOGGER.error("Error: ", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(e).build();
        }
    }
}