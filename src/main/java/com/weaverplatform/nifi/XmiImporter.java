package com.weaverplatform.nifi;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.sun.tools.doclets.internal.toolkit.util.DocFinder;
import com.weaverplatform.sdk.Entity;
import com.weaverplatform.sdk.EntityType;
import com.weaverplatform.sdk.RelationKeys;
import com.weaverplatform.sdk.Weaver;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Created by Jonathan Smit, Sysunite 2016
 */
public class XmiImporter {

  private Weaver WEAVER;
  private boolean USE_UNIX_FILEPATH;
  private String FILE;

  /**
   * Constructor
   * @param weaver instance
   * @param file specify as filename i.e. "file.xml" or unixpath i.e. "/usr/lib/input.xml"
   * @param useUnixPath "specify TRUE if @param file equals an unixpath otherwise False (read from classpath test dir)
   */
  public XmiImporter(Weaver weaver, String file, boolean useUnixPath){
    WEAVER = weaver;
    FILE = file;
    USE_UNIX_FILEPATH = useUnixPath;
  }

  /**
   * Run standalone
   * @param args
   * args[0] = weaver connection uri i.e. http://weaver:port
   * args[1] = file (see also: constructor @param file)
   */
  public static void main(String[] args){

    String weaverUrl = args[0];
    String file = args[1];

    if(weaverUrl != null && file != null) {

      System.out.println(weaverUrl);
      System.out.println(file);

      Weaver weaver = new Weaver();
      weaver.connect(weaverUrl);

      XmiImporter xmiImporter = new XmiImporter(weaver, file, true);
      String xmi = null;
      try {
        xmi = IOUtils.toString(xmiImporter.read());
      } catch (IOException e) {
        e.printStackTrace();
      }
      xmi = xmi.replaceAll("UML:", "UML.");

      String xpath = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";

      XML doc = new XMLDocument(xmi);
      List<XML> nodeClasses = doc.nodes(xpath);

      HashMap<String, String> aIndividualMap = new HashMap<>();

      for (XML nodeClass : nodeClasses) {

        String name = xmiImporter.formatName(xmiImporter.getNamedItemOnNode(nodeClass, "name"));
        String xmiID = xmiImporter.getValue(xmiImporter.getNamedItemOnNode(nodeClass, "xmi.id"));

        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("name", "Unnamed");
        Entity result = xmiImporter.toWeaverIndividual(attributes, name);

        aIndividualMap.put(xmiID, name);

      }

      xpath = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Association";

      doc = new XMLDocument(xmi);
      List<XML> nodeAssociations = doc.nodes(xpath);

      for(XML nodeAssociation : nodeAssociations){

        if(xmiImporter.getNamedItemOnNode(nodeAssociation, "name") != null) {

          String associationName = xmiImporter.getValue(xmiImporter.getNamedItemOnNode(nodeAssociation, "name"));

          xpath = "//UML.Association.connection/UML.AssociationEnd";

          List<XML> associationEnds = nodeAssociation.nodes(xpath);

          for(XML associationEnd: associationEnds){

            String type = xmiImporter.getValue(xmiImporter.getNamedItemOnNode(associationEnd, "type"));

            xpath = "//UML.ModelElement.taggedValue/UML.TaggedValue";
            List<XML> taggedValues = associationEnd.nodes(xpath);

            for(XML taggedValue : taggedValues){
              String tValue = xmiImporter.getValue(xmiImporter.getNamedItemOnNode(taggedValue, "value"));
              if(tValue.equals("source")){

                String oriParentName = aIndividualMap.get(type);

                HashMap<String, Object> attributes = new HashMap<>();
                attributes.put("label", associationName);
                attributes.put("celltype", "individual");
                xmiImporter.toWeaverAnnotation(attributes, oriParentName);

              }
            }

          }

        }
      }


    }

  }

  /**
   * Reads an xmi file from a classpath (test resource directory) or unixpath
   * @return InputStream from contents
   */
  public InputStream read(){

    try{

      if(!USE_UNIX_FILEPATH) {

        byte[] contents = FileUtils.readFileToByteArray(new File(getClass().getClassLoader().getResource(FILE).getFile()));
        InputStream in = new ByteArrayInputStream(contents);
        InputStream cont = new ByteArrayInputStream(IOUtils.toByteArray(in));
        return cont;

      }

      File f = new File(FILE);

      boolean isFile = f.exists();

      if(isFile){

        Path path = Paths.get(f.getAbsolutePath());
        byte[] data = Files.readAllBytes(path);

        InputStream in = new ByteArrayInputStream(data);
        InputStream cont = new ByteArrayInputStream(IOUtils.toByteArray(in));
        return cont;
      }

    }catch(Exception e) {
      System.out.println("cannot read!");
    }

    return null;

  }

  /**
   * fetches a Node attribute and return that attribute as Node-object
   * @param doc
   * @param attributeName
   * @return
   */
  public org.w3c.dom.Node getNamedItemOnNode(XML doc, String attributeName){
    return doc.node().getAttributes().getNamedItem(attributeName);
  }

  /**
   * Gets a value from a node i.e. an node attribute value
   * @param node
   * @return String value
   */
  public String getValue(org.w3c.dom.Node node){
    return node.getTextContent();
  }

  public String formatName(org.w3c.dom.Node node){
    String textvalue = getValue(node);

    String[] split = textvalue.split(" ");

    StringBuffer stringBuffer = new StringBuffer();
    stringBuffer.append("ib:");

    for(String name : split){
      name = stripNonCharacters(name);
      name = toCamelCase(name);
      stringBuffer.append(name);
    }

    return stringBuffer.toString();
  }

  public String stripNonCharacters(String str){

    StringBuilder result = new StringBuilder();
    for(int i=0; i<str.length(); i++) {
      char tmpChar = str.charAt(i);
      if(Character.isLetter(tmpChar)){
        result.append(tmpChar);
      }
    }

    return result.toString();

  }

  public String toCamelCase(String str){
    str = str.toLowerCase();

    String firstChar = str.substring(0,1);
    firstChar = firstChar.toUpperCase();

    String rest = str.substring(1, str.length());

    String newName = firstChar + rest;

    return newName;
  }

  public Entity toWeaverIndividual(HashMap<String, Object> attributes, String id){

    //create object
    Entity parent = WEAVER.add(attributes==null?new HashMap<String, Object>():attributes, EntityType.INDIVIDUAL, id);

    //create first annotation
    Entity aAnnotions = WEAVER.add(new HashMap<String, Object>(), EntityType.COLLECTION, WEAVER.createRandomUUID());
    parent.linkEntity(RelationKeys.ANNOTATIONS, aAnnotions);

    //create collection
    Entity aCollection = WEAVER.add(new HashMap<String, Object>(), EntityType.COLLECTION, WEAVER.createRandomUUID());
    parent.linkEntity(RelationKeys.PROPERTIES, aCollection);

    return parent;
  }

  public Entity toWeaverAnnotation(HashMap<String, Object> attributes, String parent_id){

    //retrieve parent
    Entity parent = WEAVER.get(parent_id);

    //retrieve annotions collection
    Entity aAnnotations = parent.getRelations().get(RelationKeys.ANNOTATIONS);

    //create first annotation
    Entity annotation = WEAVER.add(attributes==null?new HashMap<String, Object>():attributes, EntityType.ANNOTATION, WEAVER.createRandomUUID());
    aAnnotations.linkEntity(annotation.getId(), annotation);

    return annotation;

  }

}
