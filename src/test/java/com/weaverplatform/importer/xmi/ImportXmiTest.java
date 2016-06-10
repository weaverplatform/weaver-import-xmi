package com.weaverplatform.importer.xmi;

import com.jcabi.xml.XML;
import org.junit.Test;

import java.io.IOException;

/**
 * Created by Jonathan Smit, Sysunite 2016
 */
public class ImportXmiTest {

  private String weaverUrl = "http://docker:9487";
  private String xmiPath = "/Users/bastiaan/Desktop/InformatieBackboneModel.xml";
  private String datasetName = "model";

  @Test
  public void ImportXmiConstructorTest() throws IOException {
    ImportXmi importXmi = new ImportXmi(weaverUrl, datasetName);
    importXmi.readFromFile(xmiPath);
    importXmi.run();
  }

  @Test
  public void createWeaverDatasetTest() throws IOException {
    ImportXmi importXmi = new ImportXmi(weaverUrl, datasetName);
    importXmi.readFromFile(xmiPath);
    importXmi.createWeaverDataset();
  }


  @Test
  public void runXPath() {
    
    ImportXmi importXmi = new ImportXmi(weaverUrl, datasetName);


    importXmi.readFromFile(xmiPath);

//    System.out.println("CLASSES");
//    for(XML node : importXmi.queryXPath(ImportXmi.XPATH_TO_XMI_CLASSES)) {
//      System.out.println(node);
//    }
    System.out.println("ASSOCIATIONS");
    for(XML node : importXmi.queryXPath(ImportXmi.XPATH_TO_XMI_ASSOCIATIONS)) {
      System.out.println(node);
    }
//    System.out.println("GENERALSZ");
//    for(XML node : importXmi.queryXPath(ImportXmi.XPATH_TO_XMI_GENERALIZATIONS)) {
//      System.out.println(node);
//    }


  }
//

//
//  @Test
//  public void mapXmiClassesToWeaverIndividualsTest() {
//    ImportXmi importXmi = new ImportXmi(weaverUrl, xmiPath, datasetName);
//    XML doc = importXmi.getXML();
//    //xpath to xmi classes
//    String xpathToUMLClassNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
//    HashMap<String, String> xmiClasses = importXmi.mapXmiClasses(doc.nodes(xpathToUMLClassNodes));
//
//    assertTrue(doc.nodes(xpathToUMLClassNodes).size() > 0);
//    assertTrue(xmiClasses.size() > 0);
//  }
//
//  @Test
//  public void mapXmiClassesTest() {
//    ImportXmi importXmi = new ImportXmi(weaverUrl, xmiPath, datasetName);
//    XML doc = importXmi.getXML();
//    String xpathToUMLClassNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
//    HashMap<String, String> xmiClasses = importXmi.mapXmiClasses(doc.nodes(xpathToUMLClassNodes));
//
//    assertEquals(xmiClasses.size(), 47);
//  }
//
//  @Test
//  public void readTest() {
//    ImportXmi importXmi = new ImportXmi(weaverUrl, xmiPath, datasetName);
//    InputStream fileContents = importXmi.read();
//
//    assertTrue(fileContents != null);
//  }
//

}