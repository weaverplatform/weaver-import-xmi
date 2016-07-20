package com.weaverplatform.importer.xmi;

import com.weaverplatform.sdk.Entity;
import com.weaverplatform.sdk.EntityType;
import com.weaverplatform.sdk.ShallowEntity;
import com.weaverplatform.sdk.Weaver;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This program is written to import xmi-data and map parts of it to Weaver objects by the weaver-sdk-java.
 */
public class IndividualCreator {

  public static final String XPATH_TO_XMI_CLASSES = "//UML:Class";
  public static final String XPATH_TO_XMI_STUBS = "//EAStub[@UMLType='Class']";
  public static final String XPATH_TO_XMI_GENERALIZATIONS = "//UML:Generalization";
  public static final String XPATH_TO_XMI_DATATYPE = "UML:Attribute[@name='DataType']//UML:TaggedValue[@tag='type']";

  private Weaver weaver;
  private Entity dataset;

  private HashMap<String, String> xmiClasses;                     // XMI_ID -> individualId
  private HashMap<String, String> xmiValueClasses;                // XMI_ID -> datatype (e.g. xsd:string)

  private HashMap<String, Entity> individuals = new HashMap<>();
  private HashMap<String, Entity> predicates;

  public IndividualCreator(Weaver weaver, HashMap<String, String> xmiClasses, HashMap<String, String> xmiValueClasses, HashMap<String, Entity> predicates, Entity dataset) {

    this.weaver = weaver;

    this.xmiClasses = xmiClasses;
    this.xmiValueClasses = xmiValueClasses;
    this.predicates = predicates;

    this.dataset = dataset;
  }


  public HashMap<String, Entity> run() {



    long then, now;

    // Because we have formatted Jcabi XMLDocument, we can have special xpaths to the nodes we want
    // map the xmi classes to a hashmap
    // let jcabi fetch the xmi class nodes by the right xpath

    then = new Date().getTime();
    mapXmiClasses();
    now = new Date().getTime();
    System.out.println(now - then);



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


    then = new Date().getTime();
    createWeaverGeneralizations(ImportXmi.queryXPath(XPATH_TO_XMI_GENERALIZATIONS));
    now = new Date().getTime();
    System.out.println(now - then);

    return individuals;
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
   * Creates two hashMaps from all xmi Classes
   *
   * @return
   */
  public void mapXmiClasses() {
    xmiClasses = new HashMap<>();
    xmiValueClasses = new HashMap<>();
    NodeList classes = ImportXmi.queryXPath(XPATH_TO_XMI_CLASSES);
    for (int i = 0; i < classes.getLength(); i++) {
      Node xmiClass = classes.item(i);

      String name = deAccent(xmiClass.getAttributes().getNamedItem("name").getNodeValue());
      String xmiID = xmiClass.getAttributes().getNamedItem("xmi.id").getTextContent();

      NamedNodeMap xmlAttributes = xmiClass.getAttributes();

      Node isLeaf = xmlAttributes.getNamedItem("isLeaf");
      if(isLeaf == null) {
        continue;
      }

      boolean stringAnnotation = "true".equals(isLeaf.getNodeValue());
      if(stringAnnotation) {
        String datatype = null;
        NodeList nodes = ImportXmi.queryXPath(xmiClass, XPATH_TO_XMI_DATATYPE);
        for(int j = 0; j < nodes.getLength(); j++) {
          Node node = nodes.item(j);

          NamedNodeMap datatypeAttributes = node.getAttributes();

          Node value = datatypeAttributes.getNamedItem("value");
          if(value != null) {
            datatype = value.getNodeValue();
          }
        }
        if(datatype == null) {
//          throw new RuntimeException("Unable to find a datatype in the xmi model!");
          System.out.println("Unable to find a datatype in the xmi model!");
          continue;
        }
        xmiValueClasses.put(xmiID, datatype);
      } else {
        xmiClasses.put(xmiID, name);
      }
    }


    // Process stub mentions from xmi
    classes = ImportXmi.queryXPath(XPATH_TO_XMI_STUBS);
    for (int i = 0; i < classes.getLength(); i++) {
      Node xmiClass = classes.item(i);
      String name = deAccent(xmiClass.getAttributes().getNamedItem("name").getNodeValue());
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
      propertyAttributes.put("source", ImportXmi.source);
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
    defaultAttributes.put("source", ImportXmi.source);

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

  public static String deAccent(String str) {
    String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
    Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    return pattern.matcher(nfdNormalizedString).replaceAll("");
  }
}