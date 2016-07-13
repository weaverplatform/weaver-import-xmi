package com.weaverplatform.importer.xmi;

import com.weaverplatform.sdk.*;
import com.weaverplatform.sdk.model.Dataset;
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
import java.util.*;

/**
 * This program is written to import xmi-data and map parts of it to Weaver objects by the weaver-sdk-java.
 */
public class ImportXmi {

  public static final String XPATH_TO_XMI_CLASSES = "//UML:Class";
  public static final String XPATH_TO_XMI_STUBS = "//EAStub[@UMLType='Class']";
  public static final String XPATH_TO_XMI_ASSOCIATIONS = "//UML:Association[@name]";
  public static final String XPATH_TO_XMI_ASSOCIATION_NAMES = "//UML:Association/@name";
  public static final String XPATH_TO_XMI_ASSOCIATIONS_SOURCE = "//UML:AssociationEnd[//UML:TaggedValue/@value='source']";
  public static final String XPATH_TO_XMI_ASSOCIATIONS_TARGET = "//UML:AssociationEnd[//UML:TaggedValue/@value='target']";
  public static final String XPATH_TO_XMI_GENERALIZATIONS = "//UML:Generalization";
  public static final String XPATH_TO_XMI_DATATYPE = "//UML:Attribute[@name='DataType']//UML:TaggedValue[@tag='type']";


  private Weaver weaver;
  private String weaverUrl;
  private String datasetId;
  private String source = "xmiImporter";
  private Entity dataset;
  private InputStream inputStream;
  private Document xmldocument;

  private HashMap<String, String> xmiClasses;               // XMI_ID -> individualId
  private HashMap<String, String> xmiValueClasses;          // XMI_ID -> datatype (e.g. xsd:string)
  private HashMap<String, Entity> individuals = new HashMap<>();  // individualId -> Individual entity
  private HashMap<String, Entity> views = new HashMap<>();  // individualId -> View entity

  private HashMap<String, Entity> predicates = new HashMap<>();



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

  public void setSource(String source) {
    this.source = source;
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

//    some statistics
//    1) 1087
//    2) 1759
//    3) 13321
//    4) 14837
//    5) 1381

    // 1)
    long then, now;

    // Because we have formatted Jcabi XMLDocument, we can have special xpaths to the nodes we want
    // map the xmi classes to a hashmap
    // let jcabi fetch the xmi class nodes by the right xpath

    // 1)
    then = new Date().getTime();
    mapXmiClasses();
    now = new Date().getTime();
    System.out.println(now - then);


    // 2)
    then = new Date().getTime();

    // Map the xmiClasses to weaver as weaver individuals
    dataset = new Dataset(weaver, datasetId).create();

    // Predicates
    predicates.put("rdf:type", toWeaverPredicate("rdf:type"));
    predicates.put("rdfs:subClassOf", toWeaverPredicate("rdfs:subClassOf"));
    predicates.put("rdfs:label", toWeaverPredicate("rdfs:label"));

    NodeList nodes = queryXPath(ImportXmi.XPATH_TO_XMI_ASSOCIATION_NAMES);
    for(int i = 0; i < nodes.getLength(); i++) {
      Node node = nodes.item(i);
      String predicateName = node.getNodeValue();
      if(!predicates.containsKey(predicateName)) {
        predicates.put(predicateName, toWeaverPredicate(predicateName));
      }
    }
    now = new Date().getTime();
    System.out.println(now - then);


    // 3)
    then = new Date().getTime();

    // Individuals and Views
    individuals.put("rdfs:Class", toWeaverIndividual("rdfs:Class", false));

    Iterator<Map.Entry<String, String>> iterator = xmiClasses.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, String> pair = iterator.next();
      String xmiClassName = pair.getValue();

      if(!individuals.containsKey(xmiClassName)) {
        individuals.put(xmiClassName, toWeaverIndividual(xmiClassName, true));
      }
    }
    now = new Date().getTime();
    System.out.println(now - then);


    // 4)
    then = new Date().getTime();

    // Individuals and Views
    views.put("rdfs:Class", toWeaverView("rdfs:Class"));

    iterator = xmiClasses.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, String> pair = iterator.next();
      String xmiClassName = pair.getValue();

      if(!views.containsKey(xmiClassName)) {
        views.put(xmiClassName, toWeaverView(xmiClassName));
      }
    }
    now = new Date().getTime();
    System.out.println(now - then);


//    addFiltersToViews(queryXPath(XPATH_TO_XMI_ASSOCIATIONS));

    // 5)
    then = new Date().getTime();
    createWeaverGeneralizations(queryXPath(XPATH_TO_XMI_GENERALIZATIONS));
    now = new Date().getTime();
    System.out.println(now - then);
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
        filters.linkEntity(linkFilter.getId(), linkFilter.toShallowEntity());

      // Link to individual
      } else {

        String target = xmiClasses.get(targetId);

        Entity linkFilter = createWeaverFilter(predicate, "any-individual", "individual", "");
        filters.linkEntity(linkFilter.getId(), linkFilter.toShallowEntity());
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
    
    Entity subEntity = individuals.get(subType);
    Entity superEntity = individuals.get(superType);
    
    if(EntityType.INDIVIDUAL.equals(subEntity.getType()) && EntityType.INDIVIDUAL.equals(superEntity.getType())) {

//      Entity annotations = weaver.get(subEntity.getRelations().get("annotations").getId());
//      Entity subClassAnnotation = null;
//      for(String key : annotations.getRelations().keySet()) {
//        Entity candidateAnnotation = weaver.get(annotations.getRelations().get(key).getId());
//        if(candidateAnnotation != null && "rdfs:subClassOf".equals(candidateAnnotation.getAttributeValue("label"))) {
//          subClassAnnotation = candidateAnnotation;
//        }
//      }
      Entity properties = weaver.get(subEntity.getRelations().get("properties").getId());

//      if(subClassAnnotation == null || properties == null) {
//        throw new RuntimeException("Problem finding annotations or properties with generalization of "+subType+" --> "+superType+".");
//      }

      Map<String, ShallowEntity> relations = new HashMap<>();
      relations.put("subject", subEntity.toShallowEntity());
      relations.put("object", superEntity.toShallowEntity());
//      relations.put("annotation", subClassAnnotation);

      HashMap<String, String> propertyAttributes = new HashMap<>();
      propertyAttributes.put("source", source);
      propertyAttributes.put("predicate", predicates.get("rdfs:subClassOf").getId());

      Entity nameProperty = weaver.add(propertyAttributes, EntityType.INDIVIDUAL_PROPERTY, UUID.randomUUID().toString(), relations);


      properties.linkEntity(nameProperty.getId(), nameProperty.toShallowEntity());

    } else {
      //throw new RuntimeException("Skipping creation of generalization of "+subType+" --> "+superType+".");
    }
  }

  /**
   * Creates an Weaver Individual
   *
   * @param individualId
   * @return
   */
  public Entity toWeaverIndividual(String individualId, boolean isRdfsClass) {

    HashMap<String, String> defaultAttributes = new HashMap<>();
    defaultAttributes.put("name", individualId);
    defaultAttributes.put("source", source);

    // Create object
    Entity individual = weaver.add(defaultAttributes, EntityType.INDIVIDUAL, individualId);

    String objectsId = dataset.getRelations().get("objects").getId();
    Entity objects = weaver.get(objectsId);
    objects.linkEntity(individual.getId(), individual.toShallowEntity());

    // Create first annotation collection
//      Entity annotations = weaver.collection();
//      individual.linkEntity("annotations", annotations);

//      // Make name annotation
//      HashMap<String, Object> nameAnnotationAttributes = new HashMap<>();
//      nameAnnotationAttributes.put("label", "rdfs:label");
//      nameAnnotationAttributes.put("celltype", "string");
//      Entity nameAnnotation = toWeaverAnnotation(nameAnnotationAttributes, individualId);
//
//      // Make subclass annotation
//      HashMap<String, Object> subClassAnnotationAttributes = new HashMap<>();
//      subClassAnnotationAttributes.put("label", "rdfs:subClassOf");
//      subClassAnnotationAttributes.put("celltype", "individual");
//      toWeaverAnnotation(subClassAnnotationAttributes, individualId);

    // Create collection properties
    Entity properties = weaver.collection();
    individual.linkEntity("properties", properties.toShallowEntity());

    Map<String, ShallowEntity> relations;
    HashMap<String, String> propertyAttributes;



    relations = new HashMap<>();

    // Set rdf:type to Class property
    if(isRdfsClass) {
      relations.put("subject", individual.toShallowEntity());
      relations.put("object", individuals.get("rdfs:Class").toShallowEntity());
//      relations.put("annotation", nameAnnotation);

      propertyAttributes = new HashMap<>();
      propertyAttributes.put("predicate", predicates.get("rdfs:label").getId());

      Entity typeProperty = weaver.add(propertyAttributes, EntityType.INDIVIDUAL_PROPERTY, UUID.randomUUID().toString(), relations);
      properties.linkEntity(typeProperty.getId(), typeProperty.toShallowEntity());
    }

    // Set label property
    relations = new HashMap<>();
    relations.put("subject", individual.toShallowEntity());
//      relations.put("annotation", nameAnnotation);

    propertyAttributes = new HashMap<>();
    propertyAttributes.put("predicate", predicates.get("rdfs:label").getId());
    propertyAttributes.put("object", individualId);

    Entity nameProperty = weaver.add(propertyAttributes, EntityType.VALUE_PROPERTY, UUID.randomUUID().toString(), relations);
    properties.linkEntity(nameProperty.getId(), nameProperty.toShallowEntity());



    return individual;
  }


  public Entity toWeaverPredicate(String predicateName) {

    HashMap<String, String> defaultAttributes = new HashMap<>();
    defaultAttributes.put("name", predicateName);
    defaultAttributes.put("source", source);

    try {

      Entity predicate = weaver.add(defaultAttributes, EntityType.PREDICATE, predicateName);

      Entity predicatesCollection = weaver.get(dataset.getRelations().get("predicates").getId());
      predicatesCollection.linkEntity(predicate.getId(), predicate.toShallowEntity());

      return predicate;

    } catch (NullPointerException e) {
      throw new RuntimeException("Weaver connection error/node not found (toWeaverIndividual).");
    }
  }



  /**
   * Creates an Weaver Individual
   *
   * @param individualId
   * @return
   */
  public Entity toWeaverView(String individualId) {

    HashMap<String, String> defaultAttributes = new HashMap<>();
    defaultAttributes.put("name", individualId+" view");
    defaultAttributes.put("source", source);

    Entity view = weaver.add(defaultAttributes, EntityType.VIEW);


    Entity viewsList = weaver.get(dataset.getRelations().get("models").getId());
    viewsList.linkEntity(view.getId(), view.toShallowEntity());

    Entity filters = weaver.collection();
    view.linkEntity("filters", filters.toShallowEntity());

    Entity filter = createWeaverFilter("rdf:type", "this-individual", "individual", individualId);

    filters.linkEntity(filter.getId(), filter.toShallowEntity());

    return view;
  }

  public Entity createWeaverFilter(String predicate, String operation, String conditionType, String pointer) {

    HashMap<String, String> typeFilterAttributes = new HashMap<>();
    typeFilterAttributes.put("label", predicate);
    typeFilterAttributes.put("predicate", predicate);
    typeFilterAttributes.put("celltype", conditionType);
    Entity filter = weaver.add(typeFilterAttributes, "$FILTER");

    Entity conditions = weaver.collection();
    filter.linkEntity("conditions", conditions.toShallowEntity());

    HashMap<String, String> conditionAttributes = new HashMap<>();
    conditionAttributes.put("operation", operation);
    conditionAttributes.put("individual", pointer);
    conditionAttributes.put("conditiontype", conditionType);
    Entity condition = weaver.add(conditionAttributes, "$CONDITION");

    conditions.linkEntity(condition.getId(), condition.toShallowEntity());

    return filter;
  }

  /**
   * Creates an Weaver Annotation
   *
   * @param attributes
   * @param id
   * @return
   */
  public Entity toWeaverAnnotation(HashMap<String, String> attributes, String id) {

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
    Entity annotation = weaver.add(attributes == null ? new HashMap<String, String>() : attributes, EntityType.ANNOTATION);

    annotations.linkEntity(annotation.getId(), annotation.toShallowEntity());

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