package com.weaverplatform.importer.xmi;

import org.junit.Test;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;

/**
 * Created by Jonathan Smit, Sysunite 2016
 */
public class ImportXmiTest {

  private String weaverUrl = "http://docker:9487";
  private String xmiPath = "InformatieBackboneModel.xml";
  private String datasetName = "model";

  @Test
  public void ImportXmiConstructorTest() throws IOException {
    ImportXmi importXmi = new ImportXmi(weaverUrl, datasetName);
    importXmi.readFromResources(xmiPath);
    importXmi.run();
  }

  @Test
  public void createWeaverDatasetTest() throws IOException {
    ImportXmi importXmi = new ImportXmi(weaverUrl, datasetName);
    importXmi.readFromResources(xmiPath);
    importXmi.initDataset();
  }


  @Test
  public void runXPath() {
    
    ImportXmi importXmi = new ImportXmi(weaverUrl, datasetName);


    importXmi.readFromResources(xmiPath);

//    System.out.println("CLASSES");
//    for(XML node : importXmi.queryXPath(ImportXmi.XPATH_TO_XMI_CLASSES)) {
//      System.out.println(node);
//    }
//    System.out.println("ASSOCIATIONS");
//    for(XML node : importXmi.queryXPath(ImportXmi.XPATH_TO_XMI_ASSOCIATIONS)) {
//      System.out.println(node);
//    }
//    System.out.println("GENERALSZ");
//    for(XML node : importXmi.queryXPath(ImportXmi.XPATH_TO_XMI_GENERALIZATIONS)) {
//      System.out.println(node);
//    }
//    System.out.println("DATATYPES");
//    NodeList nodes = importXmi.queryXPath(ImportXmi.XPATH_TO_XMI_DATATYPE);
//    for(int i = 0; i < nodes.getLength(); i++) {
//      Node node = nodes.item(i);
//      System.out.println(node);
//    }
    System.out.println("ASSOCIATIONS");
    NodeList nodes = importXmi.queryXPath(ImportXmi.XPATH_TO_XMI_ASSOCIATIONS);
    for(int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      NodeList list;


      list = importXmi.queryXPath(node, ImportXmi.XPATH_TO_XMI_ASSOCIATIONS_SOURCE);
      System.out.println(list.getLength());
      for(int j = 0; j < list.getLength(); j++) {
        Node source = list.item(j);
        System.out.println(source.getAttributes().getNamedItem("type").getNodeValue());
      }

      list = importXmi.queryXPath(node, ImportXmi.XPATH_TO_XMI_ASSOCIATIONS_TARGET);
      System.out.println(list.getLength());
      for(int j = 0; j < list.getLength(); j++) {
        Node target = list.item(j);
        System.out.println(target.getAttributes().getNamedItem("type").getNodeValue());
      }
    }


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