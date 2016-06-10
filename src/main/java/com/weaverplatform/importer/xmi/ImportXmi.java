package com.weaverplatform.importer.xmi;

import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.weaverplatform.sdk.*;
import com.weaverplatform.sdk.websocket.WeaverSocket;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * This program is written to import xmi-data and map parts of it to Weaver objects by the weaver-sdk-java.
 */
public class ImportXmi {

  public static final String XPATH_TO_XMI_CLASSES = "//UML.Class";
  public static final String XPATH_TO_XMI_ASSOCIATIONS = "//UML.Association";
  public static final String XPATH_TO_XMI_GENERALIZATIONS = "//UML.Generalization";

  private Weaver weaver;
  private String weaverUrl;
  private String datasetId;
  private Entity dataset;
  private InputStream inputStream;
  private XML xmldocument;

  /**
   * Constructor
   *
   * @param weaverUrl connection string to Weaver
   */
  public ImportXmi(String weaverUrl, String datasetId) {
    this.weaverUrl = weaverUrl;
    this.datasetId = datasetId;

    weaver = new Weaver();
    weaver.connect(new WeaverSocket(URI.create(weaverUrl)));
  }

  public void readFromInputStream(InputStream inputStream) throws IOException {
    this.inputStream = inputStream;
    xmldocument = new XMLDocument(toFormattedString(inputStream));
  }

  public void readFromFile(String path) throws IOException {
    this.inputStream = createInputStream(readFromUnixPath(path));
    xmldocument = new XMLDocument(toFormattedString(inputStream));
  }

  public void readFromResources(String path) throws IOException {
    this.inputStream = createInputStream(readFromTestClassResourceDirectory(path));
    xmldocument = new XMLDocument(toFormattedString(inputStream));
  }
  
  public List<XML> queryXPath(String query) {
    return xmldocument.nodes(query);
  }

  /**
   * The start method with custom operations on this class
   *
   * @throws IOException
   */
  public void run() throws IOException {

    // Because we have formatted Jcabi XMLDocument, we can have special xpaths to the nodes we want
    // map the xmi classes to a hashmap
    // let jcabi fetch the xmi class nodes by the right xpath
    HashMap<String, String> xmiClasses = mapXmiClasses(queryXPath(XPATH_TO_XMI_CLASSES));

    // Map the xmiClasses to weaver as weaver individuals
    createWeaverDataset();
    createWeaverIndividuals(xmiClasses);
    createWeaverGeneralizations(queryXPath(XPATH_TO_XMI_GENERALIZATIONS), xmiClasses);
    createWeaverAnnotations(getAssociationsWithAttribute(queryXPath(XPATH_TO_XMI_ASSOCIATIONS), "name"), xmiClasses);
  }

  public void createWeaverDataset() {
    dataset = weaver.add(new HashMap<String, Object>(), EntityType.DATASET, datasetId);

    // Create objects collection
    Entity objects = weaver.add(new HashMap<String, Object>(), EntityType.COLLECTION, weaver.createRandomUUID());
    dataset.linkEntity(RelationKeys.OBJECTS, objects);
  }

  /**
   * Decorator method for toString(inputstream)
   * The inputstream content string is replaced by a regex and then returned as string
   *
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
   *
   * @param generalizations
   * @param xmiClasses
   */
  public void createWeaverGeneralizations(List<XML> generalizations, HashMap<String, String> xmiClasses) {
    
    for (XML generalization : generalizations) {

      NamedNodeMap attributes = generalization.node().getAttributes();

      Node subType = attributes.getNamedItem("subtype");
      Node superType = attributes.getNamedItem("supertype");
      if(subType == null || superType == null) {
        continue;
      }
      
      String subTypeUri = xmiClasses.get(subType.getNodeValue());
      String superTypeUri = xmiClasses.get(superType.getNodeValue());
      
      toWeaverGeneralization(subTypeUri, superTypeUri);
    }
  }

  /**
   * Loop trough xmi-associations and map them to weaver as annotations
   *
   * @param associations
   * @param xmiClasses
   */
  public void createWeaverAnnotations(List<XML> associations, HashMap<String, String> xmiClasses) {
    for (XML association : associations) {
      String xmiName = xmiClasses.get(getValidSubNodeValue(association));
      // Create weaver annotation
      HashMap<String, Object> attributes = new HashMap<>();
      attributes.put("label", getValueFromNode(getAttributeAsNode(association, "name")));
      attributes.put("celltype", "individual");
      toWeaverAnnotation(attributes, xmiName);
    }
  }

  /**
   * Returns the attribute type value of first subnode when a subnode of association matches with "source"
   *
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
    for (XML associationEndNode : association.nodes("//UML.Association.connection/UML.AssociationEnd")) {
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
   *
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
   *
   * @param xmiClasses
   */
  public void createWeaverIndividuals(HashMap<String, String> xmiClasses) {
    Iterator it = xmiClasses.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry pair = (Map.Entry) it.next();
      String xmiClassName = (String) pair.getValue();
      //save to weaver as Individual
      toWeaverIndividual(null, xmiClassName);
    }
  }

  /**
   * Creates one hashmap from all xmi Classes
   *
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
   *
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
      throw new RuntimeException("FileUtils.readAllBytes fail");
    }
    return null;
  }

  /**
   * Reads a file from a class path test resource directory, returns the contents as byte array
   *
   * @param path
   * @return
   */
  private byte[] readFromTestClassResourceDirectory(String path) {
    try {
      return FileUtils.readFileToByteArray(new File(getClass().getClassLoader().getResource(path).getFile()));
    } catch (IOException e) {
      throw new RuntimeException("FileUtils.readFileToByteArray fail");
    }
  }

  /**
   * Creates an inputstream object from a byte array
   *
   * @param contents
   * @return
   * @throws IOException
   */
  private InputStream createInputStream(byte[] contents) throws IOException {
    return new ByteArrayInputStream(IOUtils.toByteArray(new ByteArrayInputStream(contents)));
  }

  /**
   * Fetches a Node attribute and return that attribute as Node-object
   *
   * @param doc
   * @param attributeName
   * @return
   */
  private org.w3c.dom.Node getAttributeAsNode(XML doc, String attributeName) {
    return doc.node().getAttributes().getNamedItem(attributeName);
  }

  /**
   * Gets a value from a node i.e. an node attribute value
   *
   * @param node
   * @return
   */
  private String getValueFromNode(org.w3c.dom.Node node) {
    return node.getTextContent();
  }

  /**
   * Returns the textValue from a org.w3c.dom.Node as custom formatted String
   *
   * @param node
   * @return
   */
  private String formatName(org.w3c.dom.Node node) {
    String[] partsOfNodeAttributeValue = getValueFromNode(node).split(" ");
    StringBuffer newString = new StringBuffer();
    for (String partOfNodeAttributeValue : partsOfNodeAttributeValue) {
      partOfNodeAttributeValue = toCamelCase(stripNonCharacters(partOfNodeAttributeValue));
      newString.append(partOfNodeAttributeValue);
    }
    return newString.toString();
  }

  /**
   * Ignores characters other then letters and return the result with letters only
   *
   * @param str
   * @return
   */
  private String stripNonCharacters(String str) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < str.length(); i++) {
      char tmpChar = str.charAt(i);
      if (Character.isLetter(tmpChar)) {
        result.append(tmpChar);
      }
    }
    return result.toString();
  }

  /**
   * Transform the first char of the string to capital, and all other characters to small.
   *
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
   *
   * @param subType
   * @param superType
   * @return
   */
  public void toWeaverGeneralization(String subType, String superType) {
    
    Entity subEntity = weaver.get(subType);
    Entity superEntity = weaver.get(superType);
    
    if(EntityType.INDIVIDUAL.equals(subEntity.getType()) && EntityType.INDIVIDUAL.equals(superEntity.getType())) {

      Entity annotations = weaver.get(subEntity.getRelations().get(RelationKeys.ANNOTATIONS).getId());
      Entity subClassAnnotation = null;
      for(String key : annotations.getRelations().keySet()) {
        Entity candidateAnnotation = weaver.get(annotations.getRelations().get(key).getId());
        if(candidateAnnotation != null && "rdfs:subClassOf".equals(candidateAnnotation.getAttributeValue("label"))) {
          subClassAnnotation = candidateAnnotation;
        }
      }
      Entity properties = weaver.get(subEntity.getRelations().get(RelationKeys.PROPERTIES).getId());    
      
      if(subClassAnnotation == null || properties == null) {
        throw new RuntimeException("problem finding annotations or properties with generalization of "+subType+" --> "+superType);
      }
        
      Map<String, ShallowEntity> relations = new HashMap<>();
      relations.put("subject", subEntity);
      relations.put("object", superEntity);
      relations.put("annotation", subClassAnnotation);

      HashMap<String, Object> propertyAttributes = new HashMap<>();
      propertyAttributes.put("predicate", "rdfs:subClassOf");

      Entity nameProperty = weaver.add(propertyAttributes, EntityType.INDIVIDUAL_PROPERTY, UUID.randomUUID().toString(), relations);


      properties.linkEntity(nameProperty.getId(), nameProperty);
      
    } else {
      throw new RuntimeException("skipping creation of generalization of "+subType+" --> "+superType);
    }
  }

  /**
   * Creates an Weaver Individual
   *
   * @param attributes
   * @param individualId
   * @return
   */
  public Entity toWeaverIndividual(HashMap<String, Object> attributes, String individualId) {
    HashMap<String, Object> defaultAttributes = new HashMap<>();
    defaultAttributes.put("name", individualId);
    try {
      // Create object
      Entity individual = weaver.add(attributes == null ? defaultAttributes : attributes, EntityType.INDIVIDUAL, individualId);

      String objectsId = dataset.getRelations().get(RelationKeys.OBJECTS).getId();
      Entity objects = weaver.get(objectsId);
      objects.linkEntity(individual.getId(), individual);

      // Create first annotation collection
      Entity annotations = weaver.collection();
      individual.linkEntity(RelationKeys.ANNOTATIONS, annotations);

      // Make name annotation
      HashMap<String, Object> nameAnnotationAttributes = new HashMap<>();
      nameAnnotationAttributes.put("label", "rdfs:label");
      nameAnnotationAttributes.put("celltype", "string");
      Entity nameAnnotation = toWeaverAnnotation(nameAnnotationAttributes, individualId);

      // Make subclass annotation
      HashMap<String, Object> subClassAnnotationAttributes = new HashMap<>();
      subClassAnnotationAttributes.put("label", "rdfs:subClassOf");
      subClassAnnotationAttributes.put("celltype", "individual");
      toWeaverAnnotation(subClassAnnotationAttributes, individualId);

      // Create collection properties
      Entity properties = weaver.collection();
      individual.linkEntity(RelationKeys.PROPERTIES, properties);

      Map<String, ShallowEntity> relations = new HashMap<>();
      relations.put("subject", individual);
      relations.put("annotation", nameAnnotation);

      HashMap<String, Object> propertyAttributes = new HashMap<>();
      propertyAttributes.put("predicate", "rdfs:label");
      propertyAttributes.put("object", individualId);

      Entity nameProperty = weaver.add(propertyAttributes, EntityType.VALUE_PROPERTY, UUID.randomUUID().toString(), relations);
      properties.linkEntity(nameProperty.getId(), nameProperty);

      return individual;

    } catch (NullPointerException e) {
      throw new RuntimeException("weaver connection error/node not found. (toWeaverIndividual)");
    }
  }

  /**
   * Creates an Weaver Annotation
   *
   * @param attributes
   * @param id
   * @return
   */
  public Entity toWeaverAnnotation(HashMap<String, Object> attributes, String id) {
    try {
      // Retrieve parent
      Entity individual = weaver.get(id);

      // Retrieve annotations collection
      ShallowEntity shallowAnnotations = individual.getRelations().get(RelationKeys.ANNOTATIONS);

      // Create first annotation
      Entity annotation = weaver.add(attributes == null ? new HashMap<String, Object>() : attributes, EntityType.ANNOTATION, weaver.createRandomUUID());

      Entity aAnnotations = weaver.get(shallowAnnotations.getId());
      aAnnotations.linkEntity(annotation.getId(), annotation);

      return annotation;

    } catch (NullPointerException e) {
      throw new RuntimeException("weaver connection error/node not found. (toWeaverAnnotation)");
    }
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