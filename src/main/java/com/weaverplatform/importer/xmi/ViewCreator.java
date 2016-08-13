package com.weaverplatform.importer.xmi;

import com.weaverplatform.sdk.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This program is written to import xmi-data and map parts of it to Weaver objects by the weaver-sdk-java.
 */
public class ViewCreator {


  public static final String XPATH_TO_XMI_ASSOCIATIONS_SOURCE = "UML:ModelElement.taggedValue//UML:TaggedValue[@tag='ea_sourceName']/@value";
  public static final String XPATH_TO_XMI_ASSOCIATIONS_TARGET = "UML:ModelElement.taggedValue//UML:TaggedValue[@tag='ea_targetName']/@value";



  private Weaver weaver;
  private Entity dataset;
  private HashMap<String, String> xmiClasses;
  private HashMap<String, String> xmiValueClasses;

  HashMap<String, Entity> views;


  public ViewCreator(Weaver weaver, HashMap<String, String> xmiClasses, HashMap<String, String> xmiValueClasses, Entity dataset) {
    this.weaver = weaver;
    this.xmiClasses = xmiClasses;
    this.xmiValueClasses = xmiValueClasses;
    this.dataset = dataset;
  }





  /**
   * The start method with custom operations on this class
   *
   * @throws java.io.IOException
   */
  public HashMap<String, Entity> run() {


    long then, now;



    then = new Date().getTime();

    views = new HashMap<>();

    // Individuals and Views
    views.put("rdfs:Class", toWeaverView("rdfs:Class"));

    Iterator<Map.Entry<String, String>> iterator = xmiClasses.entrySet().iterator();
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

    now = new Date().getTime();
    System.out.println(now - then);
    return views;
  }





  public void addFiltersToViews(NodeList associations) {

    // Add extra filters
    for (int i = 0; i < associations.getLength(); i++) {

      Node association = associations.item(i);

      String sourceId = ImportXmi.queryXPath(association, XPATH_TO_XMI_ASSOCIATIONS_SOURCE).item(0).getAttributes().getNamedItem("type").getNodeValue();
      String targetId = ImportXmi.queryXPath(association, XPATH_TO_XMI_ASSOCIATIONS_TARGET).item(0).getAttributes().getNamedItem("type").getNodeValue();


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
   * Creates an Weaver Individual
   *
   * @param individualId
   * @return
   */
  public Entity toWeaverView(String individualId) {

    ConcurrentHashMap<String, String> defaultAttributes = new ConcurrentHashMap<>();
    defaultAttributes.put("name", individualId+" view");
    defaultAttributes.put("source", ImportXmi.source);

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

    ConcurrentHashMap<String, String> typeFilterAttributes = new ConcurrentHashMap<>();
    typeFilterAttributes.put("label", predicate);
    typeFilterAttributes.put("predicate", predicate);
    typeFilterAttributes.put("celltype", conditionType);
    Entity filter = weaver.add(typeFilterAttributes, "$FILTER");

    Entity conditions = weaver.collection();
    filter.linkEntity("conditions", conditions.toShallowEntity());

    ConcurrentHashMap<String, String> conditionAttributes = new ConcurrentHashMap<>();
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
  public Entity toWeaverAnnotation(ConcurrentHashMap<String, String> attributes, String id) {

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
    Entity annotation = weaver.add(attributes == null ? new ConcurrentHashMap<String, String>() : attributes, EntityType.ANNOTATION);

    annotations.linkEntity(annotation.getId(), annotation.toShallowEntity());

    return annotation;
  }



}