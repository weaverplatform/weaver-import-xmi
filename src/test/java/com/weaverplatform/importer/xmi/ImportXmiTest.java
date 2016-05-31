package com.weaverplatform.importer.xmi;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.weaverplatform.sdk.Weaver;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Created by Jonathan Smit, Sysunite 2016
 */
public class ImportXmiTest {

  private String argument0 = "http://localhost:9487";
  private String argument1 = "/users/jonathansmit/Downloads/InformatieBackboneModel.xml";


  @Test
  public void ImportXmiConstructorTest(){

    boolean result = false;

    try {

      new ImportXmi(null, null);

      result = true;

    }catch(RuntimeException e){
      System.out.println(e.getMessage());
    }

    assertFalse(result);

  }

  @Test
  public void mapXmiAssociationsToWeaverAnnotationsTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);

    XML doc = importXmi.getXML();

    //xpath to xmi classes
    String xpath = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";

    HashMap<String, String> xmiClasses = importXmi.mapXmiClasses(doc.nodes(xpath));

    assertEquals(xmiClasses.size(), 47);

    importXmi.mapXmiClassesToWeaverIndividuals(xmiClasses);

    xpath = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Association";

    importXmi.mapXmiAssociationsToWeaverAnnotations(doc.nodes(xpath), xmiClasses);

      //assert??
  }

  @Test
  public void mapSpecificXmiAssociationToWeaverAnnotationTest(){

  }

  @Test
  public void mapXmiClassesToWeaverIndividualsTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);

      XML doc = importXmi.getXML();

      //xpath to xmi classes
      String xpath = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";

      assertTrue(doc.nodes(xpath).size() > 0);

      HashMap<String, String> xmiClasses = importXmi.mapXmiClasses(doc.nodes(xpath));

      assertTrue(xmiClasses.size() > 0);
  }

  @Test
  public void mapXmiClassesTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);

    XML doc = importXmi.getXML();

    //xpath to xmi classes
    String xpath = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";

    HashMap<String, String> xmiClasses = importXmi.mapXmiClasses(doc.nodes(xpath));

    assertEquals(xmiClasses.size(), 47);
  }

  @Test
  public void notNullTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);

    String contents = null;
    assertFalse(importXmi.notNull(contents));

    contents = "hello";
    assertTrue(importXmi.notNull(contents));

    XML doc = importXmi.getXML();
    String xpathToClassNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
    List<XML> nodes = doc.nodes(xpathToClassNodes);

    org.w3c.dom.Node node = importXmi.getAttributeAsNode(nodes.get(0), "name");

    assertTrue(importXmi.notNull(node));

  }

  @Test
  public void readTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);

    String fileContents = importXmi.getFileContents();

    assertTrue(fileContents != null);
    assertTrue(fileContents.length() > 0);

  }

  @Test
  public void hasPathTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);
    String filePath = "/unix";
    assertTrue(importXmi.hasPath(filePath));
  }

  @Test
  public void getAttributeAsNodeTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);
    XML doc = importXmi.getXML();
    String xpathToClassNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
    List<XML> nodes = doc.nodes(xpathToClassNodes);

    assertTrue(importXmi.notNull(importXmi.getAttributeAsNode(nodes.get(0), "name")));
  }

  @Test
  public void getValueFromNodeTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);
    XML doc = importXmi.getXML();
    String xpathToClassNodes = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
    List<XML> nodes = doc.nodes(xpathToClassNodes);

    String nodeValue = importXmi.getValueFromNode(importXmi.getAttributeAsNode(nodes.get(0), "name"));
    assertTrue(importXmi.notNull(nodeValue));
    assertTrue(nodeValue.length() > 0);
  }

  @Test
  public void formatNameTest(){

  }

  @Test
  public void stripNonCharactersTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);
    String characters = "(foo bar)";
    String result = importXmi.stripNonCharacters(characters);
    assertEquals("foobar", result);
  }

  @Test
  public void toCamelCaseTest(){
    ImportXmi importXmi = new ImportXmi(argument0, argument1);
    String word = "woo";
    String result = importXmi.toCamelCase(word);
    assertEquals("Woo", result);
  }

  @Test
  public void toWeaverIndividualTest(){

  }

  @Test
  public void toWeaverAnnotationTest(){

  }
}