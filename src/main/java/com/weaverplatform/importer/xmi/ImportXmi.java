package com.weaverplatform.importer.xmi;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.weaverplatform.sdk.*;
import com.weaverplatform.sdk.websocket.WeaverSocket;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * This program is written to import xmi-data and map parts of it to Weaver objects by the weaver-sdk-java.
 */
public class ImportXmi {

  public static final String XPATH_TO_XMI_CLASSES      = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Class";
  public static final String XPATH_TO_XMI_ASSOCIATIONS = "//XMI.content/UML.Model/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Package/UML.Namespace.ownedElement/UML.Association";
  
  private Weaver weaver;
  private String weaverUrl;
  private String datasetId;
  private Entity dataset;
  private InputStream inputStream;

  /**
   * Constructor
   * @param weaverUrl connection string to Weaver
   */
  public ImportXmi(String weaverUrl, String datasetId) {
    this.weaverUrl = weaverUrl;
    this.datasetId = datasetId;
    
    weaver = new Weaver();
    weaver.connect(new WeaverSocket(URI.create(weaverUrl)));
  }

  public void readFromFile(String path) throws IOException {
    this.inputStream = createInputStream(readFromUnixPath(path));
  }

  public void readFromResources(String path) throws IOException {
    this.inputStream = createInputStream(readFromTestClassResourceDirectory(path));
  }

  /**
   * The start method with custom operations on this class
   * @throws IOException
   */
  public void run() throws IOException {
    //read the xml file and get a formatted Jcabi XML document.
    XML xmldocument = new XMLDocument(toFormattedString(inputStream));
    
    //because we have formatted Jcabi XMLDocument, we can have special xpaths to the nodes we want
    //map the xmi classes to a hashmap
    //let jcabi fetch the xmi class nodes by the right xpath
    HashMap<String, String> xmiClasses = mapXmiClasses(xmldocument.nodes(XPATH_TO_XMI_CLASSES));
    
    //map the xmiClasses to weaver as weaver individuals
    createWeaverDataset();
    createWeaverIndividuals(xmiClasses);
    createWeaverAnnotations(getAssociationsWithAttribute(xmldocument.nodes(XPATH_TO_XMI_ASSOCIATIONS), "name"), xmiClasses);
  }

  public void createWeaverDataset(){
    dataset = weaver.add(new HashMap<String, Object>(), EntityType.DATASET, datasetId);

    //create objects collection
    Entity objects = weaver.add(new HashMap<String, Object>(), EntityType.COLLECTION, weaver.createRandomUUID());
    dataset.linkEntity(RelationKeys.OBJECTS, objects);
  }
  
  /**
   * Decorator method for toString(inputstream)
   * The inputstream content string is replaced by a regex and then returned as string
   * @param contents
   * @return
   * @throws IOException
   */
  public String toFormattedString(InputStream contents) throws IOException {
    //replace ':' to ignore xml namespace errors while reading with xpath
    return IOUtils.toString(contents).replaceAll("UML:", "UML.");
  }

  /**
   * Loop trough xmi-associations and map them to weaver as annotations
   * @param associations
   * @param xmiClasses
   */
  public void createWeaverAnnotations(List<XML> associations, HashMap<String, String> xmiClasses) {
    for (XML association : associations) {
      String xmiName = xmiClasses.get(getValidSubNodeValue(association));
      //create weaver annotation
      HashMap<String, Object> attributes = new HashMap<>();
      attributes.put("label", getValueFromNode(getAttributeAsNode(association, "name")));
      attributes.put("celltype", "individual");
      toWeaverAnnotation(attributes, xmiName);
    }
  }

  /**
   * Returns the attribute type value of first subnode when a subnode of association matches with "source"
   * @param association
   * @return
   */
  private String getValidSubNodeValue(XML association) {
    /** currentAssociation node
     *
     * <association>
     *     <connection>
     *         <end type=xmiId>
     *            <taggedValues>
     *                <taggedValue value=source></taggedValue>
     *            </taggedValues>
     *         </end>
     *     </connection>
     * </association>
     *
     */
    for(XML associationEndNode : association.nodes("//UML.Association.connection/UML.AssociationEnd")) {
      for (XML associationTaggedValueNode : associationEndNode.nodes("//UML.ModelElement.taggedValue/UML.TaggedValue")) {
        if (getValueFromNode(getAttributeAsNode(associationTaggedValueNode, "value")).equals("source")) {
          return getValueFromNode(getAttributeAsNode(associationEndNode, "type"));
        }
      }
    }
    return null;
  }

  /**
   * Returns a list with XML Association Nodes which have a specific attribute
   * @param associations
   * @param attributeName
   * @return
   */
  public List<XML> getAssociationsWithAttribute(List<XML> associations, String attributeName) {
    List<XML> filteredAssociations = new ArrayList<>();
    for (XML association : associations) {
      if (getAttributeAsNode(association, attributeName) != null) {
        filteredAssociations.add(association);
      }
    }
    return filteredAssociations;
  }

  /**
   * Save the name of an xmi-class as weaver individual
   * @param xmiClasses
   */
  public void createWeaverIndividuals(HashMap<String, String> xmiClasses) {
    Iterator it = xmiClasses.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry pair = (Map.Entry)it.next();
      String xmiClassName = (String)pair.getValue();
      //save to weaver as Individual
      toWeaverIndividual(null, xmiClassName);
    }
  }

  /**
   * Creates one hashmap from all xmi Classes
   * @param xmiClasses
   * @return
   */
  public HashMap<String, String> mapXmiClasses(List<XML> xmiClasses) {
    HashMap<String, String> map = new HashMap<String, String>();
    for (XML xmiClass : xmiClasses) {
      String name = formatName(getAttributeAsNode(xmiClass, "name"));
      String xmiID = getValueFromNode(getAttributeAsNode(xmiClass, "xmi.id"));
      map.put(xmiID, name);
    }
    return map;
  }


  /**
   * Reads a file from a unix path, returns the contents as byte array
   * @param path
   * @return
   */
  private byte[] readFromUnixPath(String path) {
    try {
      File f = new File(path);
      boolean isFile = f.exists();
      if (isFile) {
        return Files.readAllBytes(Paths.get(f.getAbsolutePath()));
      }
    } catch (IOException e) {
      System.out.println("FileUtils.readAllBytes fail");
    }
    return null;
  }

  /**
   * Reads a file from a class path test resource directory, returns the contents as byte array
   * @param path
   * @return
   */
  private byte[] readFromTestClassResourceDirectory(String path) {
    try {
      return FileUtils.readFileToByteArray(new File(getClass().getClassLoader().getResource(path).getFile()));
    } catch(IOException e) {
      System.out.println("FileUtils.readFileToByteArray fail");
    }
    return null;
  }

  /**
   * Creates an inputstream object from a byte array
   * @param contents
   * @return
   * @throws IOException
   */
  private InputStream createInputStream(byte[] contents) throws IOException {
    return new ByteArrayInputStream(IOUtils.toByteArray(new ByteArrayInputStream(contents)));
  }

  /**
   * Fetches a Node attribute and return that attribute as Node-object
   * @param doc
   * @param attributeName
   * @return
   */
  private org.w3c.dom.Node getAttributeAsNode(XML doc, String attributeName) {
    return doc.node().getAttributes().getNamedItem(attributeName);
  }

  /**
   * Gets a value from a node i.e. an node attribute value
   * @param node
   * @return
   */
  private String getValueFromNode(org.w3c.dom.Node node){
    return node.getTextContent();
  }

  /**
   * Returns the textValue from a org.w3c.dom.Node as custom formatted String
   * @param node
   * @return
   */
  private String formatName(org.w3c.dom.Node node) {
    String[] partsOfNodeAttributeValue  = getValueFromNode(node).split(" ");
    StringBuffer newString = new StringBuffer();
    for (String partOfNodeAttributeValue : partsOfNodeAttributeValue) {
      partOfNodeAttributeValue = toCamelCase(stripNonCharacters(partOfNodeAttributeValue));
      newString.append(partOfNodeAttributeValue);
    }
    return newString.toString();
  }

  /**
   * Ignores characters other then letters and return the result with letters only
   * @param str
   * @return
   */
  private String stripNonCharacters(String str) {
    StringBuilder result = new StringBuilder();
    for (int i=0; i<str.length(); i++) {
      char tmpChar = str.charAt(i);
      if (Character.isLetter(tmpChar)) {
        result.append(tmpChar);
      }
    }
    return result.toString();
  }

  /**
   * Transform the first char of the string to capital, and all other characters to small.
   * @param str
   * @return
   */
  private String toCamelCase(String str) {
    str = str.toLowerCase();
    String firstCharAsCapital = str.substring(0, 1).toUpperCase();
    String charactersWithoutFirstChar = str.substring(1, str.length());
    return (firstCharAsCapital + charactersWithoutFirstChar);
  }

  /**
   * Creates an Weaver Individual
   * @param attributes
   * @param individualId
   * @return
   */
  public Entity toWeaverIndividual(HashMap<String, Object> attributes, String individualId) {
    HashMap<String, Object> defaultAttributes = new HashMap<>();
    defaultAttributes.put("name", individualId);
    try {
      //create object
      Entity parent = weaver.add(attributes == null ? defaultAttributes : attributes, EntityType.INDIVIDUAL, individualId);

      String objectsId = dataset.getRelations().get(RelationKeys.OBJECTS).getId();
      Entity objects   = weaver.get(objectsId);
      objects.linkEntity(parent.getId(), parent);

      //create first annotation collection
      Entity aAnnotations = weaver.add(new HashMap<String, Object>(), EntityType.COLLECTION, weaver.createRandomUUID());
      parent.linkEntity(RelationKeys.ANNOTATIONS, aAnnotations);

      //make one annotation for every individual
      HashMap<String, Object> annotationAttributes = new HashMap<>();
      annotationAttributes.put("label", individualId);
      annotationAttributes.put("celltype", "individual");
      toWeaverAnnotation(annotationAttributes, individualId);

      //create collection properties
      Entity aCollection = weaver.add(new HashMap<String, Object>(), EntityType.COLLECTION, weaver.createRandomUUID());
      parent.linkEntity(RelationKeys.PROPERTIES, aCollection);

      return parent;

    } catch (NullPointerException e) {
      System.out.println("weaver connection error/node not found. (toWeaverIndividual)");
    }
    return null;
  }

  /**
   * Creates an Weaver Annotation
   * @param attributes
   * @param id
   * @return
   */
  public Entity toWeaverAnnotation(HashMap<String, Object> attributes, String id) {
    try {
      //retrieve parent
      Entity parent = weaver.get(id);

      //retrieve annotions collection
      ShallowEntity shallowAnnotations = parent.getRelations().get(RelationKeys.ANNOTATIONS);

      //create first annotation
      Entity annotation = weaver.add(attributes == null ? new HashMap<String, Object>() : attributes, EntityType.ANNOTATION, weaver.createRandomUUID());

      Entity aAnnotations = weaver.get(shallowAnnotations.getId());
      aAnnotations.linkEntity(annotation.getId(), annotation);

      return annotation;

    } catch (NullPointerException e) {
      System.out.println("weaver connection error/node not found. (toWeaverAnnotation)");
    }
    return null;
  }

  public Weaver getWeaver() {
    return weaver;
  }

  public String getWeaverUrl() {
    return weaverUrl;
  }

  public String getDatasetId() {
    return datasetId;
  }

  public Entity getDataset() {
    return dataset;
  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public void setInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }
}