package org.hl7.davinci.ehrserver;

import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.jpa.api.config.DaoConfig;
import ca.uhn.fhir.jpa.api.dao.IFhirSystemDao;
import ca.uhn.fhir.jpa.provider.JpaCapabilityStatementProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.UriType;

import javax.servlet.http.HttpServletRequest;


public class ServerConformanceR4 extends JpaCapabilityStatementProvider {

  public ServerConformanceR4(RestfulServer theRestfulServer, IFhirSystemDao<Bundle, Meta> theSystemDao, DaoConfig theDaoConfig, ISearchParamRegistry theSearchParamRegistry, IValidationSupport theValidationSupport) {
    super(theRestfulServer, theSystemDao, theDaoConfig, theSearchParamRegistry, theValidationSupport);
  }

  @Override
  public CapabilityStatement getServerConformance(HttpServletRequest theRequest, RequestDetails details) {
    Extension securityExtension = new Extension();
    securityExtension.setUrl("http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");
    String proxy_authorize = System.getenv("PROXY_AUTHORIZE") != null ?
        System.getenv("PROXY_AUTHORIZE") : Config.get("proxy_authorize");

    String  proxy_token =  System.getenv("PROXY_TOKEN") != null ?
        System.getenv("PROXY_TOKEN") : Config.get("proxy_token");

    securityExtension.addExtension()
        .setUrl("authorize")
        .setValue(new UriType(proxy_authorize));
    securityExtension.addExtension()
        .setUrl("token")
        .setValue(new UriType(proxy_token));
    CapabilityStatement.CapabilityStatementRestSecurityComponent securityComponent = new CapabilityStatement.CapabilityStatementRestSecurityComponent();
    securityComponent.setCors(true);
    securityComponent
        .addExtension(securityExtension);

    CapabilityStatement regularConformance = (CapabilityStatement) super.getServerConformance(theRequest, details);
    regularConformance.getRest().get(0).setSecurity(securityComponent);
    return regularConformance;
  }
}
