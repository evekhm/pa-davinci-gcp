package org.hl7.davinci.priorauth;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.hl7.davinci.priorauth.Endpoint.RequestType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Claim.RelatedClaimComponent;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.hl7.fhir.r4.model.ClaimResponse.Use;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.parser.IParser;

/**
 * The Claim endpoint to READ, SEARCH for, and DELETE submitted claims.
 */
@RequestScoped
@Path("Claim")
public class ClaimEndpoint {

  static final Logger logger = LoggerFactory.getLogger(ClaimEndpoint.class);

  String REQUIRES_BUNDLE = "Prior Authorization Claim/$submit Operation requires a Bundle with a single Claim as the first entry and supporting resources.";
  String PROCESS_FAILED = "Unable to process the request properly. Check the log for more details.";

  @Context
  private UriInfo uri;

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response readClaimJson(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    return Endpoint.read(id, patient, status, Database.CLAIM, uri, RequestType.JSON);
  }

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response readClaimXml(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    return Endpoint.read(id, patient, status, Database.CLAIM, uri, RequestType.XML);
  }

  @DELETE
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response deleteClaimJson(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Database.CLAIM, RequestType.JSON);
  }

  @DELETE
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+xml" })
  public Response deleteClaimXml(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Database.CLAIM, RequestType.XML);
  }

  @POST
  @Path("/$submit")
  @Consumes({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response submitOperationJson(String body) {
    return submitOperation(body, RequestType.JSON);
  }

  @POST
  @Path("/$submit")
  @Consumes({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response submitOperationXml(String body) {
    return submitOperation(body, RequestType.XML);
  }

  /**
   * The submitOperation function for both json and xml
   * 
   * @param body        - the body of the post request.
   * @param requestType - the RequestType of the request.
   * @return - claimResponse response
   */
  private Response submitOperation(String body, RequestType requestType) {
    logger.info("POST /Claim/$submit fhir+" + requestType.name());
    String id = null;
    Status status = Status.OK;
    String formattedData = null;
    try {
      IParser parser = requestType == RequestType.JSON ? App.FHIR_CTX.newJsonParser() : App.FHIR_CTX.newXmlParser();
      IBaseResource resource = parser.parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        if (bundle.hasEntry() && (bundle.getEntry().size() > 1) && bundle.getEntryFirstRep().hasResource()
            && bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim) {
          IBaseResource response = processBundle(bundle);
          if (response == null) {
            // Failed processing bundle...
            status = Status.BAD_REQUEST;
            OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, PROCESS_FAILED);
            formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
          } else {
            if (response.getIdElement().hasIdPart()) {
              id = response.getIdElement().getIdPart();
            }
            formattedData = requestType == RequestType.JSON ? App.DB.json(response) : App.DB.xml(response);
          }
        } else {
          // Claim is required...
          status = Status.BAD_REQUEST;
          OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
          formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
        }
      } else {
        // Bundle is required...
        status = Status.BAD_REQUEST;
        OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
        formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
      }
    } catch (Exception e) {
      // The submission failed so spectacularly that we need
      // catch an exception and send back an error message...
      status = Status.BAD_REQUEST;
      OperationOutcome error = App.DB.outcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
    }
    ResponseBuilder builder = requestType == RequestType.JSON
        ? Response.status(status).type("application/fhir+json").entity(formattedData)
        : Response.status(status).type("application/fhir+xml").entity(formattedData);
    if (id != null) {
      builder = builder.header("Location", uri.getBaseUri() + "ClaimResponse/" + id);
    }
    return builder.build();
  }

  /**
   * Process the $submit operation Bundle. Theoretically, this is where business
   * logic should be implemented or overridden.
   * 
   * @param bundle Bundle with a Claim followed by other required resources.
   * @return ClaimResponse with the result.
   */
  private IBaseResource processBundle(Bundle bundle) {
    logger.info("processBundle");
    // Store the submission...
    // Generate a shared id...
    String id = UUID.randomUUID().toString();

    // get the patient
    Claim claim = (Claim) bundle.getEntryFirstRep().getResource();
    String patient = "";
    try {
      String[] patientParts = claim.getPatient().getReference().split("/");
      patient = patientParts[patientParts.length - 1];
      logger.info("processBundle: patient: " + patientParts[patientParts.length - 1]);
    } catch (Exception e) {
      logger.error("processBundle: error procesing patient: " + e.toString());
    }

    String claimId = id;
    String responseDisposition = "Unknown";
    ClaimResponseStatus responseStatus = ClaimResponseStatus.ACTIVE;
    ClaimStatus status = claim.getStatus();
    if (status == Claim.ClaimStatus.CANCELLED) {
      // Cancel the claim...
      claimId = claim.getIdElement().getIdPart();
      if (cancelClaim(claimId, patient)) {
        responseStatus = ClaimResponseStatus.CANCELLED;
        responseDisposition = "Cancelled";
      } else
        return null;
    } else {
      // Store the claim...
      claim.setId(id);
      String claimStatusStr = Database.getStatusFromResource(claim);
      Map<String, Object> claimMap = new HashMap<String, Object>();
      claimMap.put("id", id);
      claimMap.put("patient", patient);
      claimMap.put("status", claimStatusStr);
      claimMap.put("resource", claim);
      RelatedClaimComponent related = getRelatedComponent(claim);
      if (related != null)
        claimMap.put("related", related.getIdElement().asStringValue());
      if (!App.DB.write(Database.CLAIM, claimMap))
        return null;

      // Store the bundle...
      bundle.setId(id);
      Map<String, Object> bundleMap = new HashMap<String, Object>();
      bundleMap.put("id", id);
      bundleMap.put("patient", patient);
      bundleMap.put("status", Database.getStatusFromResource(bundle));
      bundleMap.put("resource", bundle);
      App.DB.write(Database.BUNDLE, bundleMap);

      // Store the claim items...
      if (claim.hasItem()) {
        if (related != null) {
          // Update the items...
          for (ItemComponent item : claim.getItem()) {
            Map<String, Object> dataMap = new HashMap<String, Object>();
            Map<String, Object> constraintMap = new HashMap<String, Object>();
            constraintMap.put("id", related.getIdElement().asStringValue());
            constraintMap.put("sequence", item.getSequence());
            dataMap.put("id", id);
            dataMap.put("status", ClaimStatus.CANCELLED.getDisplay().toLowerCase());
            App.DB.update(Database.CLAIM_ITEM, constraintMap, dataMap);
          }
        } else {
          // Add the claim items...
          for (ItemComponent item : claim.getItem()) {
            Map<String, Object> itemMap = new HashMap<String, Object>();
            itemMap.put("id", id);
            itemMap.put("sequence", item.getSequence());
            itemMap.put("status", claimStatusStr);
            App.DB.write(Database.CLAIM_ITEM, itemMap);
          }
        }
      }

      // Make up a disposition
      responseDisposition = "Granted";
    }
    // Process the claim...
    // TODO

    // Generate the claim response...
    ClaimResponse response = generateClaimResponse(id, responseDisposition, responseStatus, claim);

    // Store the claim respnose...
    Map<String, Object> responseMap = new HashMap<String, Object>();
    responseMap.put("id", id);
    responseMap.put("claimId", claimId);
    responseMap.put("patient", patient);
    responseMap.put("status", Database.getStatusFromResource(response));
    responseMap.put("resource", response);
    App.DB.write(Database.CLAIM_RESPONSE, responseMap);

    // Respond...
    return response;
  }

  /**
   * Determine if a cancel can be performed and then update the DB to reflect the
   * cancel
   * 
   * @param claimId - the claim id to cancel.
   * @param patient - the patient for the claim to cancel.
   * @return true if the claim was cancelled successfully and false otherwise
   */
  private boolean cancelClaim(String claimId, String patient) {
    boolean result;
    Claim initialClaim = (Claim) App.DB.read(Database.CLAIM, claimId, patient);
    if (initialClaim != null) {
      if (initialClaim.getStatus() != Claim.ClaimStatus.CANCELLED) {
        Map<String, Object> dataMap = new HashMap<String, Object>();
        Map<String, Object> constraintMap = new HashMap<String, Object>();
        constraintMap.put("id", claimId);
        dataMap.put("status", Claim.ClaimStatus.CANCELLED.getDisplay().toLowerCase());
        App.DB.update(Database.CLAIM, constraintMap, dataMap);
        result = true;
      } else {
        logger.info("Claim " + claimId + " is already cancelled");
        result = false;
      }
    } else {
      logger.info("Claim " + claimId + " does not exist. Unable to cancel");
      result = false;
    }
    return result;
  }

  /**
   * Get the related claim for an update to a claim (replaces relationship)
   * 
   * @param claim - the base Claim resource.
   * @return the first related claim with relationship "replaces" or null if no
   *         matching related resource.
   */
  private RelatedClaimComponent getRelatedComponent(Claim claim) {
    if (claim.hasRelated()) {
      for (RelatedClaimComponent relatedComponent : claim.getRelated()) {
        if (relatedComponent.hasRelationship()) {
          for (Coding code : relatedComponent.getRelationship().getCoding()) {
            if (code.getCode().equals("replaces")) {
              // This claim is an update to an old claim
              return relatedComponent;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Internal function to create the ClaimResponse to be sent back
   * 
   * @param id                  - the id for this response.
   * @param responseDisposition - the disposition.
   * @param responseStatus      - the status.
   * @param claim               - the Claim resource this is a response to.
   * @return the ClaimResponse with properties set correctly.
   */
  private ClaimResponse generateClaimResponse(String id, String responseDisposition, ClaimResponseStatus responseStatus,
      Claim claim) {
    ClaimResponse response = new ClaimResponse();
    response.setStatus(responseStatus);
    response.setType(claim.getType());
    response.setUse(Use.PREAUTHORIZATION);
    response.setPatient(claim.getPatient());
    response.setCreated(new Date());
    if (claim.hasInsurer()) {
      response.setInsurer(claim.getInsurer());
    } else {
      response.setInsurer(new Reference().setDisplay("Unknown"));
    }
    response.setRequest(new Reference(uri.getBaseUri() + "Claim/" + id));
    response.setOutcome(RemittanceOutcome.COMPLETE);
    response.setDisposition(responseDisposition);
    response.setPreAuthRef(id);
    // TODO response.setPreAuthPeriod(period)?
    response.setId(id);
    return response;
  }
}
