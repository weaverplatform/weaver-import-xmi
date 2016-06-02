package com.weaverplatform.importer.xmi;

import com.jcabi.xml.XML;
import org.junit.Test;

import java.io.InputStream;
import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Created by Jonathan Smit, Sysunite 2016
 */
public class ImportXmiTest {

  private String argument0 = "http://localhost:9487";
  private String argument1 = "/users/jonathansmit/Downloads/InformatieBackboneModel.xml";
  private String argument2 = "testDataset";


  @Test
  public void ImportXmiConstructorTest() {
    boolean result = false;
    try {
      new ImportXmi(null, null, null);
      result = true;
    } catch (RuntimeException e) {
      System.out.println(e.getMessage());
    }

    assertFalse(result);
  }

  @Test
  public void mapXmiAssociationsToWeaverAnnotationsTest() {
    ImportXmi importXmi = new ImportXmi(argument0, argument1, argument2);
    XML doc = importXmi.getFormatedXML();
    //xpath to xmi classes
    String xpathToUMLClassNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
    String xpathToUMLAssociationNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Association";
    HashMap<String, String> xmiClasses = importXmi.mapXmiClasses(doc.nodes(xpathToUMLClassNodes));
    importXmi.createWeaverIndividuals(xmiClasses);
    importXmi.createWeaverAnnotations(importXmi.getAssociationsWithAttribute(doc.nodes(xpathToUMLAssociationNodes), "name"), xmiClasses);

    assertEquals(xmiClasses.size(), 47);
  }

  @Test
  public void mapSpecificXmiAssociationToWeaverAnnotationTest() {

  }

  @Test
  public void mapXmiClassesToWeaverIndividualsTest() {
    ImportXmi importXmi = new ImportXmi(argument0, argument1, argument2);
    XML doc = importXmi.getXML();
    //xpath to xmi classes
    String xpathToUMLClassNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
    HashMap<String, String> xmiClasses = importXmi.mapXmiClasses(doc.nodes(xpathToUMLClassNodes));

    assertTrue(doc.nodes(xpathToUMLClassNodes).size() > 0);
    assertTrue(xmiClasses.size() > 0);
  }

  @Test
  public void mapXmiClassesTest() {
    ImportXmi importXmi = new ImportXmi(argument0, argument1, argument2);
    XML doc = importXmi.getXML();
    String xpathToUMLClassNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
    HashMap<String, String> xmiClasses = importXmi.mapXmiClasses(doc.nodes(xpathToUMLClassNodes));

    assertEquals(xmiClasses.size(), 47);
  }

  @Test
  public void readTest() {
    ImportXmi importXmi = new ImportXmi(argument0, argument1, argument2);
    InputStream fileContents = importXmi.read();

    assertTrue(fileContents != null);
  }

  @Test
  public void toWeaverIndividualTest() {

  }

  @Test
  public void toWeaverAnnotationTest() {

  }
}