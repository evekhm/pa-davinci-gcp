package org.hl7.davinci.endpoint.files.gcp;

import com.google.cloud.storage.StorageException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import org.apache.commons.io.FilenameUtils;
import org.hl7.davinci.endpoint.cql.CqlExecution;
import org.hl7.davinci.endpoint.cql.CqlRule;
import org.hl7.davinci.endpoint.database.FhirResource;
import org.hl7.davinci.endpoint.files.CDSLibrarySourceProvider;
import org.hl7.davinci.endpoint.files.CommonFileStore;
import org.hl7.davinci.endpoint.files.FileResource;
import org.hl7.davinci.endpoint.files.FileStore;
import org.hl7.davinci.endpoint.vsac.ValueSetCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.apache.commons.io.FileUtils;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Component
@Profile("gcp")
public class GCPFileStore extends CommonFileStore {
  static final Logger logger = LoggerFactory.getLogger(GCPFileStore.class);

  @Value("${google.storage.bucket}")
  private String bucketName;

  @Value("${google.storage.db}")
  private String objectName;

  @Value("${google.storage.examplesPath}")
  private String examples;

  @Value("${google.storage.rulesPath}")
  private String rules;

  @Value("${google.storage.projectId}")
  private String projectId;

  @Value("${google.pod.zip}")
  private String destFilePath;

  @Value("${google.pod.path}")
  private String db;


  @Autowired
  public GCPFileStore() {
    logger.info("Using GCPFileStore");
  }

  public void reload() {

    long startTime = System.nanoTime();
    boolean success = true;

    // clear the database first
    lookupTable.deleteAll();
    fhirResources.deleteAll();

    logger.info(String.format("GCPFileStore::reload() projectId=%s, db=%s, bucket=%s",
        projectId, objectName, bucketName));

    logger.info(System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    Blob blob = null;

    try {
      blob = storage.get(bucketName, objectName);
    } catch (StorageException e) {
      logger.error("GCPFileStore::reload() - Unable to get blob from GCP " + e);
    }

    if (blob != null) {
      try {
        //Check for destination directory
        File destDirectory = new File(destFilePath);
        File parentPath = destDirectory.getParentFile();
        boolean readyForDownload = true;
        if (!parentPath.exists()){
          try {
            readyForDownload = parentPath.mkdirs();
          } catch (SecurityException exc) {
            readyForDownload = false;
            logger.error(String.format("GCPFileStore::reload() - Unable to create destination directory %s - %s", destFilePath, exc));
          }
        }

        if (readyForDownload && destDirectory.exists() && destDirectory.isDirectory()){
          logger.info(String.format("GCPFileStore::reload() - Removing existing library, to reload with the new one %s - %s/%s",
              destDirectory, bucketName, objectName));
          try {
            FileUtils.deleteDirectory(destDirectory);
          } catch (IOException exc) {
            readyForDownload = false;
            logger.error(String.format("GCPFileStore::reload() - Unable to delete existing directory destination directory %s - %s", destDirectory, exc));
          }
        }
        logger.info(String.valueOf(readyForDownload));
        if(readyForDownload) {
          logger.info(String.format("GCPFileStore::reload() - Downloading to %s", destFilePath));
          blob.downloadTo(Paths.get(destFilePath));
          success = reloadFromZip(destFilePath, rules, examples, false);
        }
        else
          logger.error("GCPFileStore::reload() - Could not create local directory " + destFilePath);

      } catch (StorageException e) {
        logger.error("GCPFileStore::reload() - Unable to download blob data inside the pod - " + e);
      }
    }

    long endTime = System.nanoTime();
    long timeElapsed = endTime - startTime;
    float seconds = (float) timeElapsed / (float) 1000000000;

    if (success) {
      logger.info("GCPFileStore::reload(): completed in " + seconds + " seconds, downloaded to " + destFilePath);
    } else {
      logger.warn("GCPFileStore::reload(): failed in " + seconds + " seconds");
    }
  }

  private String getExamplesPath(){
    File destFolder = new File(db);
    File examplesFolder = new File(destFolder, examples);
    return examplesFolder.getPath() + "/";
  }

  private String getRulesPath(){
    File destFolder = new File(db);
    File examplesFolder = new File(destFolder, rules);
    return examplesFolder.getPath() + "/";
  }

  public CqlRule getCqlRule(String topic, String fhirVersion) {
    logger.info("GCPFileStore::getCqlRule(): " + topic + "/" + fhirVersion);

    // load CQL files needed for the CRD Rule
    HashMap<String, byte[]> cqlFiles = new HashMap<>();


    String mainCqlLibraryName = topic + "Rule";
    File mainCqlFile = findFile(getRulesPath(), topic, fhirVersion, mainCqlLibraryName, FileStore.CQL_EXTENSION);

    // look for the main cql file in the examples path as well
    if (mainCqlFile == null) {
      mainCqlFile = findFile(getExamplesPath(), topic, fhirVersion, mainCqlLibraryName, FileStore.CQL_EXTENSION);
    }
    if (mainCqlFile == null) {
      logger.warn("GCPFileStore::getCqlRule(): failed to find main CQL file");
    } else {
      try {
        cqlFiles.put(mainCqlFile.getName(), Files.readAllBytes(mainCqlFile.toPath()));
        logger.info("GCPFileStore::getCqlRule(): added mainCqlFile: " + mainCqlFile.toPath());
      } catch (IOException e) {
        logger.warn("GCPFileStore::getCqlRule(): failed to open main cql file: " + e.getMessage());
      }
    }

    File helperCqlFile = findFile(getRulesPath(), FileStore.SHARED_TOPIC, fhirVersion, FileStore.FHIR_HELPERS_FILENAME, FileStore.CQL_EXTENSION);

    // look for the helper cql file in the examples path as well
    if (helperCqlFile == null) {
      helperCqlFile = findFile(getExamplesPath(), FileStore.SHARED_TOPIC, fhirVersion, FileStore.FHIR_HELPERS_FILENAME, FileStore.CQL_EXTENSION);
    }
    if (helperCqlFile == null) {
      logger.warn("GCPFileStore::getCqlRule(): failed to find FHIR helper CQL file");
    } else {
      try {
        cqlFiles.put(helperCqlFile.getName(), Files.readAllBytes(helperCqlFile.toPath()));
        logger.info("GCPFileStore::getCqlRule(): added helperCqlFile: " + helperCqlFile.toPath());
      } catch (IOException e) {
        logger.warn("GCPFileStore::getCqlRule(): failed to open file FHIR helper cql file: " + e.getMessage());
      }
    }

    return new CqlRule(mainCqlLibraryName, cqlFiles, fhirVersion);
  }


  public FileResource getFile(String topic, String fileName, String fhirVersion, boolean convert) {
    FileResource fileResource = new FileResource();
    fileResource.setFilename(fileName);


    String partialFilePath = topic + "/" + fhirVersion + "/files/" + fileName;
    String filePath = getRulesPath() + partialFilePath;
    File file = new File(filePath);

    if (!Files.exists(file.toPath())) {
      logger.info("GCPFileStore::getFile(): could not find file: " + file.toString() + " will try examples folder");

      filePath = getExamplesPath() + partialFilePath;
      file = new File(filePath);
      if (!Files.exists(file.toPath())) {
        logger.warn("GCPFileStore::getFile(): could not find file: " + file.toString());
        return null;
      }
    }

    byte[] fileData = null;
    try {
      fileData = Files.readAllBytes(file.toPath());

      // convert to ELM
      if (convert && FilenameUtils.getExtension(fileName).equalsIgnoreCase("CQL")) {
        logger.info("GCPFileStore::getFile() converting CQL to JSON ELM");
        String cql = new String(fileData);
        try {
          String elm = CqlExecution.translateToElm(cql, new CDSLibrarySourceProvider(this));
          fileData = elm.getBytes();
        } catch (Exception e) {
          logger.warn("GCPFileStore::getFile() Error: could not convert CQL: " + e.getMessage());
          return null;
        }
      }
    } catch (IOException e) {
      logger.warn("GCPFileStore::getFile() failed to get file: " + e.getMessage());
      return null;
    }

    fileResource.setResource(new ByteArrayResource(fileData));
    return fileResource;
  }

  protected String readFhirResourceFromFile(FhirResource fhirResource, String fhirVersion) {
    String fileString = null;
    String filePath;

    File file = null;

    // If the topic indicates it's actually from the ValueSet cache. Grab file path from there.
    if (fhirResource.getTopic().equals(ValueSetCache.VSAC_TOPIC)) {
      filePath = config.getValueSetCachePath() + fhirResource.getFilename();
      file = new File(filePath);
      logger.warn("Atempting to serve valueset from cache at: " + filePath);
    } else {
      String partialFilePath = fhirResource.getTopic() + "/" + fhirVersion + "/resources/" + fhirResource.getFilename();
      filePath = getRulesPath() + partialFilePath;
      file = new File(filePath);

      if (!Files.exists(file.toPath())) {
        logger.info("GCPFileStore::readFhirResourceFromFile(): could not find file: " + file.toString() + " will try examples folder");

        filePath = getExamplesPath() + partialFilePath;
        file = new File(filePath);
        if (!Files.exists(file.toPath())) {
          logger.warn("GCPFileStore::readFhirResourceFromFile(): could not find file: " + file.toString());
          return null;
        }
      }

      logger.warn("Attemping to serve file from: " + filePath);
    }

    try {
      byte[] fileData = Files.readAllBytes(file.toPath());
      fileString = new String(fileData, Charset.defaultCharset());
    } catch (IOException e) {
      logger.warn("GCPFileStore::readFhirResourceFromFile() failed to get file: " + e.getMessage());
      return null;
    }

    return fileString;
  }

}
