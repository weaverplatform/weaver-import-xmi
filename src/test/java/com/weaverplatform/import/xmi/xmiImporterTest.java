package com.weaverplatform.import.xmi;

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
public class xmiImporterTest {

  public XmiImporter xmiImporter;

  @Before
  public void init(){

    Weaver weaver = new Weaver();
    weaver.connect("http://localhost:9487");

    this.xmiImporter = new XmiImporter(weaver, "InformatieBackboneModel.xml", false); //important! set to true if run from xmiImporterTest.class
  }

  @Test
  public void mainTest(){

    //optional modification: replace namespace UML to have a valid xpath later on
    String xmi = null;
    try {
      xmi = IOUtils.toString(xmiImporter.read());
    } catch (IOException e) {
      e.printStackTrace();
    }
    xmi = xmi.replaceAll("UML:", "UML.");

    //because we altered the xmlString, we can now fetch nodes with a valid xpath
    String xpath = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";

    XML doc = new XMLDocument(xmi);
    List<XML> nodes = doc.nodes(xpath);

    assertEquals(nodes.size(), 47);


    HashMap<String, String> aMap = new HashMap<>(); //{UUID, xmiID}

    for (XML node : nodes){

      String textvalue = xmiImporter.formatName(xmiImporter.getNamedItemOnNode(node, "name"));

      String[] split = textvalue.split(" ");

      StringBuffer stringBuffer = new StringBuffer();
      stringBuffer.append("ib:");

      for(String name : split){

        name = name.toLowerCase();

        String firstChar = name.substring(0,1);
        firstChar = firstChar.toUpperCase();

        String rest = name.substring(1, name.length());

        String newName = firstChar + rest;

        stringBuffer.append(newName);

      }

      System.out.println(stringBuffer.toString());  //"Foo Bar" becomes: "FooBar"

      //xmiImporter.toWeaverIndividual(null, xmiID);

      //System.out.println(UUID.randomUUID().toString() + " -- " + xmiID);

      aMap.put(UUID.randomUUID().toString(), stringBuffer.toString());


    }

    assertEquals(aMap.size(), 47);

  }

}
