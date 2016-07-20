package com.weaverplatform.importer.xmi;

import com.weaverplatform.sdk.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Date;
import java.util.HashMap;

/**
 * This program is written to import xmi-data and map parts of it to Weaver objects by the weaver-sdk-java.
 */
public class PredicateCreator {

  public static final String XPATH_TO_XMI_ASSOCIATION_NAMES = "//UML:Association/@name";
  public static final String XPATH_TO_XMI_ASSOCIATIONS = "//UML:Association[@name]";
  public static final String XPATH_TO_XMI_ASSOCIATIONS_SOURCE = "UML:ModelElement.taggedValue//UML:TaggedValue[@tag='ea_sourceName']/@value";
  public static final String XPATH_TO_XMI_ASSOCIATIONS_TARGET = "UML:ModelElement.taggedValue//UML:TaggedValue[@tag='ea_targetName']/@value";

  private Weaver weaver;
  private Entity dataset;

  private HashMap<String, Entity> predicates = new HashMap<>();
  private HashMap<String, Entity> subPropertyCollections = new HashMap<>();
  private HashMap<String, String> domains = new HashMap<>();
  private HashMap<String, String> ranges = new HashMap<>();

  public PredicateCreator(Weaver weaver, Entity dataset) {
    this.weaver = weaver;
    this.dataset = dataset;
  }


  public HashMap<String, Entity> run() {

    long then, now;
    then = new Date().getTime();

    // Predicates
    predicates.put("rdf:type", toWeaverPredicate("rdf:type"));
    predicates.put("rdfs:subClassOf", toWeaverPredicate("rdfs:subClassOf"));
    predicates.put("rdfs:label", toWeaverPredicate("rdfs:label"));

    NodeList nodes = ImportXmi.queryXPath(PredicateCreator.XPATH_TO_XMI_ASSOCIATION_NAMES);
    for(int i = 0; i < nodes.getLength(); i++) {
      Node association = nodes.item(i);
      String predicateName = association.getNodeValue();

      if(!predicates.containsKey(predicateName)) {
        Entity predicate = toWeaverPredicate(predicateName);
        Entity collection = weaver.collection();
        subPropertyCollections.put(predicateName, collection);
        predicate.linkEntity("subProperties", collection.toShallowEntity());
        predicates.put(predicateName, predicate);
      }
    }
    now = new Date().getTime();
    System.out.println(now - then);

    // 2b)
    then = new Date().getTime();

    nodes = ImportXmi.queryXPath(PredicateCreator.XPATH_TO_XMI_ASSOCIATIONS);

    for(int i = 0; i < nodes.getLength(); i++) {
      Node association = nodes.item(i);

      String nameId = ImportXmi.queryXPath(association, "@name").item(0).getNodeValue();
      String sourceId = ImportXmi.queryXPath(association, XPATH_TO_XMI_ASSOCIATIONS_SOURCE).item(0).getNodeValue();
      String targetId = ImportXmi.queryXPath(association, XPATH_TO_XMI_ASSOCIATIONS_TARGET).item(0).getNodeValue();

      String predicateId = domainPredicateRangeToId(sourceId, nameId, targetId);

      if(!predicates.containsKey(predicateId)) {
        System.out.println("Predicate "+predicateId+" was defined twice!");
        continue;
      }
      Entity predicate = toWeaverPredicate(predicateId, predicates.get(nameId).toShallowEntity());
      subPropertyCollections.get(nameId).linkEntity(predicateId, predicate.toShallowEntity());
      predicates.put(predicateId, predicate);

      domains.put(predicateId, sourceId);
      ranges.put(predicateId, targetId);
    }
    now = new Date().getTime();
    System.out.println(now - then);

    return predicates;
  }

  public void setDomainAndRange(HashMap<String, Entity> individuals) {
    for(String predicateId : predicates.keySet()) {
      Entity domainEntity = individuals.get(domains.get(predicateId));
      Entity rangeEntity = individuals.get(ranges.get(predicateId));
      Entity predicate = predicates.get(predicateId);

      predicate.updateEntityLink("domain", domainEntity.toShallowEntity());
      predicate.updateEntityLink("range",  rangeEntity.toShallowEntity());
    }
  }





  public Entity toWeaverPredicate(String predicateId) {
    return toWeaverPredicate(predicateId, null);
  }
  public Entity toWeaverPredicate(String predicateId, ShallowEntity subPropertyOf) {

    HashMap<String, ShallowEntity> relations = new HashMap<>();
    if(subPropertyOf != null) {
      relations.put("super", subPropertyOf);
    }

    HashMap<String, String> attributes = new HashMap<>();
    attributes.put("preferredName", predicateNameFromId(predicateId));
    attributes.put("reversedName", predicateReversedNameFromId(predicateId));
    attributes.put("source", ImportXmi.source);



    Entity predicate = weaver.add(attributes, EntityType.PREDICATE, predicateId, relations);

    if(subPropertyOf == null) {
      try {
        Entity predicatesCollection = weaver.get(dataset.getRelations().get("predicates").getId());
        predicatesCollection.linkEntity(predicate.getId(), predicate.toShallowEntity());
      } catch (EntityNotFoundException e) {
        throw new RuntimeException("Weaver connection error/node not found (toWeaverIndividual).");
      }
    }

    return predicate;


  }




  private String domainPredicateRangeToId(String domain, String predicate, String range) {
    String result = "lib:";
    result += domain.replace(":","");
    result += "-";
    result += predicate.replace(":","");
    result += "-";
    result += range.replace(":","");
    return result;
  }

  private String predicateNameFromId(String predicateId) {
    String predicate = predicateId.split(":")[1];
    String result = "";
    for (int i = 0; i < predicate.length(); i++) {
      char c = predicate.charAt(i);
      if(Character.isUpperCase(c)) {
        if (result.length() > 0) {
          result += " ";
        }
        result += String.valueOf(c).toLowerCase();
      } else {
        result += String.valueOf(c);
      }
    }
    return result;
  }

  private String predicateReversedNameFromId(String predicateId) {

    String predicateName = predicateNameFromId(predicateId);
    String result = "";
    for (int i = predicateName.length()-1; i > -1; i--) {
      char c = predicateName.charAt(i);
      result += String.valueOf(c);
    }
    return result;
  }
}