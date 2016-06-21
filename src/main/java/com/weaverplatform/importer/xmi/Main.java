package com.weaverplatform.importer.xmi;

import java.io.IOException;

public class Main {

  /**
   * Run standalone
   * @param args
   * args[0] = weaver connection uri i.e. http://weaver:port
   * args[1] = filePath (see also: constructor @param filePath)
   * args[2] = name of model of weaver workbench                 
   */
  public static void main(String[] args) throws IOException {
    String weaverUrl, filePath, datasetId;
    
    weaverUrl = args[0];
    filePath  = args[1];
    datasetId = args[2];
    
    ImportXmi importXmi = new ImportXmi(weaverUrl, datasetId);
    importXmi.readFromFile(filePath);
    importXmi.run();
  }
}