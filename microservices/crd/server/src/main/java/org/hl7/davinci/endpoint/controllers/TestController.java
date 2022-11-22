package org.hl7.davinci.endpoint.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;
import org.hl7.davinci.endpoint.Application;
import org.hl7.davinci.endpoint.config.YamlConfig;
import org.hl7.davinci.endpoint.database.Client;
import org.hl7.davinci.endpoint.database.ClientRepository;
import org.hl7.davinci.endpoint.database.RequestLog;
import org.hl7.davinci.endpoint.database.RequestRepository;
import org.hl7.davinci.endpoint.database.RuleMapping;
import org.hl7.davinci.endpoint.files.FileResource;
import org.hl7.davinci.endpoint.files.FileStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;


/**
 * Provides the REST interface that can be interacted with at [base]/api/test.
 */
@RestController
public class TestController {
  private static Logger logger = Logger.getLogger(Application.class.getName());


  @Autowired
  private YamlConfig myConfig;


  @GetMapping(value = "/api/test")
  @CrossOrigin
  public String testConfig() {
    logger.info("testConfig: GET /api/test");
    String result = "launchUrl = " + myConfig.getLaunchUrl().toString() + "<br>";
    result += "localDb Path = " + myConfig.getLocalDb().getPath() + "<br>";
    result += "Allowed CORS  = " + myConfig.getCorsOriginsAndEndPoints().toString() + "<br>";
    return result;
  }

}
