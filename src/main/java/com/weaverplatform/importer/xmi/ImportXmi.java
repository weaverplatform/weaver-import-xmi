package com.weaverplatform.importer.xmi;

import com.weaverplatform.sdk.Entity;
import com.weaverplatform.sdk.Weaver;
import com.weaverplatform.sdk.model.Dataset;
import com.weaverplatform.sdk.websocket.WeaverSocket;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
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

/**
 * This program is written to import xmi-data and map parts of it to Weaver objects by the weaver-sdk-java.
 */
public class ImportXmi {

  private Weaver weaver;
  private String weaverUrl;
  private String datasetId;
  public static String source = "xmiImporter";
  private Entity dataset;
  private InputStream inputStream;
  private static Document xmldocument;

  private HashMap<String, String> xmiClasses;                     // XMI_ID -> individualId
  private HashMap<String, String> xmiValueClasses;                // XMI_ID -> datatype (e.g. xsd:string)
  private HashMap<String, Entity> individuals;                    // individualId -> Individual entity
  private HashMap<String, Entity> views = new HashMap<>();        // individualId -> View entity

  private HashMap<String, Entity> predicates;



  public ImportXmi(Weaver weaver, String datasetId) {
    this.datasetId = datasetId;
    this.weaver = weaver;
  }

  public ImportXmi(String weaverUrl, String datasetId) {
    this.weaverUrl = weaverUrl;
    this.datasetId = datasetId;

    this.weaver = new Weaver("ins:");
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







  
  public static NodeList queryXPath(Node node, String query) {
    XPath xpath = XPathFactory.newInstance().newXPath();
    xpath.setNamespaceContext(new NamespaceResolver(xmldocument));
    try {
      return (NodeList) xpath.evaluate(query, node, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new RuntimeException("XPath query could not be executed.", e);
    }
  }

  public static NodeList queryXPath(String query) {
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

    // Init dataset
    dataset = new Dataset(weaver, datasetId).get(datasetId);

    // Init lists of all classes
    xmiClasses = new HashMap<>();
    xmiValueClasses = new HashMap<>();

    PredicateCreator predicateCreator = new PredicateCreator(weaver, dataset);
    predicates = predicateCreator.run();

    IndividualCreator individualCreator = new IndividualCreator(weaver, xmiClasses, xmiValueClasses, predicates, dataset);
    individuals = individualCreator.run();

//    predicateCreator.setDomainAndRange(individuals);

    ViewCreator viewCreator = new ViewCreator(weaver, xmiClasses, xmiValueClasses, dataset);
    views = viewCreator.run();
  }

  public void close() {

    // Close Weaver connection
    weaver.close();
  }






  public Weaver getWeaver() {
    return weaver;
  }

  public String getWeaverUrl() {
    return weaverUrl;
  }



  public InputStream getInputStream() {
    return inputStream;
  }

  public void setInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }



  static class NamespaceResolver implements NamespaceContext {

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