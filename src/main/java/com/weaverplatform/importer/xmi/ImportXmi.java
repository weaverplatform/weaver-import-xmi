package com.weaverplatform.importer.xmi;

import com.weaverplatform.sdk.*;
import com.weaverplatform.sdk.websocket.WeaverSocket;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * This program is written to import xmi-data and map parts of it to Weaver objects by the weaver-sdk-java.
 */
public class ImportXmi {

  public static final String XPATH_TO_XMI_CLASSES = "//UML:Class";
  public static final String XPATH_TO_XMI_STUBS = "//EAStub[@UMLType='Class']";
  public static final String XPATH_TO_XMI_ASSOCIATIONS = "//UML:Association[@name]";
  public static final String XPATH_TO_XMI_ASSOCIATIONS_SOURCE = "//UML:AssociationEnd[//UML:TaggedValue/@value='source']";
  public static final String XPATH_TO_XMI_ASSOCIATIONS_TARGET = "//UML:AssociationEnd[//UML:TaggedValue/@value='target']";
  public static final String XPATH_TO_XMI_GENERALIZATIONS = "//UML:Generalization";
  public static final String XPATH_TO_XMI_DATATYPE = "//UML:Attribute[@name='DataType']//UML:TaggedValue[@tag='type']";


  private Weaver weaver;
  private String weaverUrl;
  private String datasetId;
  private Entity dataset;
  private InputStream inputStream;
  private Document xmldocument;

  private HashMap<String, String> xmiClasses;               // XMI_ID -> individualId
  private HashMap<String, String> xmiValueClasses;          // XMI_ID -> datatype (e.g. xsd:string)
  private HashMap<String, Entity> views = new HashMap<>();  // individualId -> View entity



  public ImportXmi(Weaver weaver, String datasetId) {
    this.datasetId = datasetId;
    this.weaver = weaver;
  }

  public ImportXmi(String weaverUrl, String datasetId) {
    this.weaverUrl = weaverUrl;
    this.datasetId = datasetId;

    this.weaver = new Weaver();
    this.weaver.connect(new WeaverSocket(URI.create(weaverUrl)));
  }






  public void readFromInputStream(InputStream inputStream) {

    DocumentBuilderFactory domFactory;
    DocumentBuilder builder;

    try {
      domFactory = DocumentBuilderFactory.newInstance();
      domFactory.setNamespaceAware(true);
      builder = domFactory.newDocumentBuilder();
      this.inputStream = inputStream;
      xmldocument = builder.parse(inputStream);
    } catch (Exception e) {
      throw new RuntimeException("Problem reading inputStream", e);
    }
  }

  public void readFromFile(String path) {
    try {
      File f = new File(path);
      if (f.exists()) {
        byte[] content = Files.readAllBytes(Paths.get(f.getAbsolutePath()));

        readFromInputStream(new ByteArrayInputStream(IOUtils.toByteArray(new ByteArrayInputStream(content))));

      } else {
        throw new RuntimeException("File "+path+" not found!");
      }
    } catch (IOException e) {
      throw new RuntimeException("FileUtils.readAllBytes fail");
    }
  }

  public void readFromResources(String path) {

    try {
      byte[] content =  FileUtils.readFileToByteArray(new File(getClass().getClassLoader().getResource(path).getFile()));
      readFromInputStream(new ByteArrayInputStream(IOUtils.toByteArray(new ByteArrayInputStream(content))));

    } catch (IOException e) {
      throw new RuntimeException("FileUtils.readFileToByteArray fail");
    }
  }







  
  public NodeList queryXPath(Node node, String query) {
    XPath xpath = XPathFactory.newInstance().newXPath();
    xpath.setNamespaceContext(new NamespaceResolver(xmldocument));
    try {
      return (NodeList) xpath.evaluate(query, node, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new RuntimeException("XPath query could not be executed.", e);
    }
  }

  public NodeList queryXPath(String query) {
    XPath xpath = XPathFactory.newInstance().newXPath();
    xpath.setNamespaceContext(new NamespaceResolver(xmldocument));
    try {
      return (NodeList) xpath.evaluate(query, xmldocument, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new RuntimeException("XPath query could not be executed.", e);
    }
  }

  /**
   * The start method with custom operations on this class
   *
   * @throws IOException
   */
  public void run() {

    // Because we have formatted Jcabi XMLDocument, we can have special xpaths to the nodes we want
    // map the xmi classes to a hashmap
    // let jcabi fetch the xmi class nodes by the right xpath
    mapXmiClasses();

    // Map the xmiClasses to weaver as weaver individuals
    initDataset();
    createWeaverIndividualsAndViews();
    addFiltersToViews(queryXPath(XPATH_TO_XMI_ASSOCIATIONS));
    createWeaverGeneralizations(queryXPath(XPATH_TO_XMI_GENERALIZATIONS));
  }

  public void close() {

    // Close Weaver connection
    weaver.close();
  }



  /**
   * Loop trough xmi-associations and map them to weaver as annotations
   *
   * @param generalizations
   */
  public void createWeaverGeneralizations(NodeList generalizations) {
    
    for (int i = 0; i < generalizations.getLength(); i++) {

      Node generalization = generalizations.item(i);
      NamedNodeMap attributes = generalization.getAttributes();

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
   * Save the name of an xmi-class as weaver individual
   */
  public void createWeaverIndividualsAndViews() {
    Iterator<Map.Entry<String, String>> iterator = xmiClasses.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, String> pair = iterator.next();
      String xmiClassName = pair.getValue();
      toWeaverIndividual(null, xmiClassName);
      toWeaverView(xmiClassName);
    }
  }

  public void addFiltersToViews(NodeList associations) {

    // Add extra filters
    for (int i = 0; i < associations.getLength(); i++) {

      Node association = associations.item(i);

      String sourceId = queryXPath(association, XPATH_TO_XMI_ASSOCIATIONS_SOURCE).item(0).getAttributes().getNamedItem("type").getNodeValue();
      String targetId = queryXPath(association, XPATH_TO_XMI_ASSOCIATIONS_TARGET).item(0).getAttributes().getNamedItem("type").getNodeValue();


      if(!xmiClasses.containsKey(sourceId)) {
        System.out.println("Id "+sourceId+" not in the xmiClasses list.");
        continue;
      }

      String individualId = xmiClasses.get(sourceId);

      if(!views.containsKey(individualId)) {
        System.out.println("Id "+individualId+" not in the views list.");
        continue;
      }

      Entity view = views.get(individualId);
      Entity filters = weaver.get(view.getRelations().get("filters").getId());

      // Create weaver filter
      String predicate = association.getAttributes().getNamedItem("name").getTextContent();

      // Link to string
      if(xmiValueClasses.containsKey(targetId)) {


        String datatype = xmiValueClasses.get(targetId);

        Entity linkFilter = createWeaverFilter(predicate, "any-value", "string", "");
        filters.linkEntity(linkFilter.getId(), linkFilter);

      // Link to individual
      } else {

        String target = xmiClasses.get(targetId);

        Entity linkFilter = createWeaverFilter(predicate, "any-individual", "individual", "");
        filters.linkEntity(linkFilter.getId(), linkFilter);
      }

    }
  }

  /**
   * Creates two hashMaps from all xmi Classes
   *
   * @return
   */
  public void mapXmiClasses() {
    xmiClasses = new HashMap<>();
    xmiValueClasses = new HashMap<>();
    NodeList classes = queryXPath(XPATH_TO_XMI_CLASSES);
    for (int i = 0; i < classes.getLength(); i++) {
      Node xmiClass = classes.item(i);

      String name = xmiClass.getAttributes().getNamedItem("name").getNodeValue();
      String xmiID = xmiClass.getAttributes().getNamedItem("xmi.id").getTextContent();

      NamedNodeMap xmlAttributes = xmiClass.getAttributes();

      Node isLeaf = xmlAttributes.getNamedItem("isLeaf");
      if(isLeaf == null) {
        continue;
      }

      boolean stringAnnotation = "true".equals(isLeaf.getNodeValue());
      if(stringAnnotation) {
        String datatype = null;
        NodeList nodes = queryXPath(xmiClass, XPATH_TO_XMI_DATATYPE);
        for(int j = 0; j < nodes.getLength(); j++) {
          Node node = nodes.item(j);

          NamedNodeMap datatypeAttributes = node.getAttributes();

          Node value = datatypeAttributes.getNamedItem("value");
          if(value != null) {
            datatype = value.getNodeValue();
          }
        }
        if(datatype == null) {
          throw new RuntimeException("Unable to find a datatype in the xmi model!");
        }
        xmiValueClasses.put(xmiID, datatype);
      } else {
        xmiClasses.put(xmiID, name);
      }
    }


    // Process stub mentions from xmi
    classes = queryXPath(XPATH_TO_XMI_STUBS);
    for (int i = 0; i < classes.getLength(); i++) {
      Node xmiClass = classes.item(i);
      String name = xmiClass.getAttributes().getNamedItem("name").getNodeValue();
      String xmiID = xmiClass.getAttributes().getNamedItem("xmi.id").getTextContent();
      xmiClasses.put(xmiID, name);
    }
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
        throw new RuntimeException("Problem finding annotations or properties with generalization of "+subType+" --> "+superType+".");
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
      //throw new RuntimeException("Skipping creation of generalization of "+subType+" --> "+superType+".");
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
      throw new RuntimeException("Weaver connection error/node not found (toWeaverIndividual).");
    }
  }

  public void initDataset() {

    dataset = weaver.get(datasetId);
    if(!EntityType.DATASET.equals(dataset.getType())) {
      weaver.add(new HashMap<String, Object>(), EntityType.DATASET, datasetId);
      dataset.linkEntity("objects", weaver.collection());
      dataset.linkEntity("views", weaver.collection());
    }

  }

  /**
   * Creates an Weaver Individual
   *
   * @param individualId
   * @return
   */
  public Entity toWeaverView(String individualId) {

    HashMap<String, Object> defaultAttributes = new HashMap<>();
    defaultAttributes.put("name", individualId+" view");

    Entity view = weaver.add(defaultAttributes, EntityType.VIEW);
    views.put(individualId, view);

    Entity viewsList = weaver.get(dataset.getRelations().get("views").getId());
    viewsList.linkEntity(view.getId(), view);

    Entity filters = weaver.collection();
    view.linkEntity("filters", filters);

    Entity filter = createWeaverFilter("rdf:type", "this-individual", "individual", individualId);

    filters.linkEntity(filter.getId(), filter);

    return view;
  }

  public Entity createWeaverFilter(String predicate, String operation, String conditionType, String pointer) {

    HashMap<String, Object> typeFilterAttributes = new HashMap<>();
    typeFilterAttributes.put("label", predicate);
    typeFilterAttributes.put("predicate", predicate);
    typeFilterAttributes.put("celltype", conditionType);
    Entity filter = weaver.add(typeFilterAttributes, "$FILTER");

    Entity conditions = weaver.collection();
    filter.linkEntity("conditions", conditions);

    HashMap<String, Object> conditionAttributes = new HashMap<>();
    conditionAttributes.put("operation", operation);
    conditionAttributes.put("individual", pointer);
    conditionAttributes.put("conditiontype", conditionType);
    Entity condition = weaver.add(conditionAttributes, "$CONDITION");

    conditions.linkEntity(condition.getId(), condition);

    return filter;
  }

  /**
   * Creates an Weaver Annotation
   *
   * @param attributes
   * @param id
   * @return
   */
  public Entity toWeaverAnnotation(HashMap<String, Object> attributes, String id) {

    // Retrieve parent
    Entity individual = weaver.get(id);
    if(individual == null) {
      throw new RuntimeException("Individual "+id+" not foud.");
    }

    // Retrieve annotations collection
    ShallowEntity shallowAnnotations = individual.getRelations().get(RelationKeys.ANNOTATIONS);
    if(shallowAnnotations == null) {
      throw new RuntimeException("Annotations not found for "+id);
    }
    Entity annotations = weaver.get(shallowAnnotations.getId());

    // Create first annotation
    Entity annotation = weaver.add(attributes == null ? new HashMap<String, Object>() : attributes, EntityType.ANNOTATION);

    annotations.linkEntity(annotation.getId(), annotation);

    return annotation;
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



  class NamespaceResolver implements NamespaceContext {

    private final Document document;

    public NamespaceResolver(Document document) {
      this.document = document;
    }

    public String getNamespaceURI(String prefix) {
      if (prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
        return document.lookupNamespaceURI(null);
      } else {
        return document.lookupNamespaceURI(prefix);
      }
    }

    public String getPrefix(String namespaceURI) {
      return document.lookupPrefix(namespaceURI);
    }

    @SuppressWarnings("rawtypes")
    public Iterator getPrefixes(String namespaceURI) {
      // not implemented
      return null;
    }
  }
}