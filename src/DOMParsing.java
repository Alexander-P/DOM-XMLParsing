import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DOMParsing {

  public static void main(String[] args) {
    String xmlPath = "ex7.xml";
    String xsdPath = "restaurant_schema.xsd";
    Document doc;

    // The XML document is parsed and validated against its XSD to ensure data integrity and schema compliance.
    try {
      doc = parseXMLWithXSD(xmlPath, xsdPath);
      System.out.println("XML is valid and parsed successfully!");
    } catch (ParserConfigurationException | SAXException | IOException e) {
      System.err.println("Validation/Parsing failed: " + e.getMessage());
      e.printStackTrace();
      return;
    } catch (Exception e) {
      System.err.println("Unexpected error during XML load: " + e.getMessage());
      e.printStackTrace();
      return;
    }

    XPathFactory xPathFactory = XPathFactory.newInstance();
    XPath xpath = xPathFactory.newXPath();

    NodeList dishNodes;
    try {
      dishNodes = (NodeList) xpath.evaluate("//dish", doc, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new RuntimeException("Failed to evaluate XPath for <dish> elements.", e);
    }

    // Dish type totals are maintained to calculate average prices.
    Map<String, Double> typePriceMap = new HashMap<>();
    Map<String, Integer> typeCountMap = new HashMap<>();
    // This approach helps assess the overall complexity of the menu.
    int totalIngredients = 0;

    for (int i = 0; i < dishNodes.getLength(); i++) {
      Element dish = (Element) dishNodes.item(i);
      String type = dish.getAttribute("type");
      String priceStr = dish.getAttribute("price");

      double price = Double.parseDouble(priceStr);

      // Prices and counts are aggregated to derive an average price per dish type.
      typePriceMap.put(type, typePriceMap.getOrDefault(type, 0.0) + price);
      typeCountMap.put(type, typeCountMap.getOrDefault(type, 0) + 1);

      // Ingredients are counted to identify the total volume of items used in dishes.
      NodeList ingredientsList = dish.getElementsByTagName("ingredient");
      totalIngredients += ingredientsList.getLength();
    }

    // The code sums up ratings to understand overall customer satisfaction.
    NodeList reviewNodes;
    try {
      reviewNodes = (NodeList) xpath.evaluate("//review", doc, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new RuntimeException("Failed to evaluate XPath for <review> elements.", e);
    }

    double ratingSum = 0.0;
    for (int i = 0; i < reviewNodes.getLength(); i++) {
      Element review = (Element) reviewNodes.item(i);
      double rating = Double.parseDouble(review.getAttribute("rating"));
      ratingSum += rating;
    }
    double averageRating = (reviewNodes.getLength() > 0)
        ? (ratingSum / reviewNodes.getLength()) : 0.0;

    // Total weekly open hours are tracked to provide insight into operational duration.
    NodeList dayNodes;
    try {
      dayNodes = (NodeList) xpath.evaluate("//day", doc, XPathConstants.NODESET);
    } catch (XPathExpressionException e) {
      throw new RuntimeException("Failed to evaluate XPath for <day> elements.", e);
    }

    double totalOpenHours = 0.0;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");

    for (int i = 0; i < dayNodes.getLength(); i++) {
      Element day = (Element) dayNodes.item(i);
      // If closed="true", skip it to avoid skewing operational hour calculations.
      if ("true".equals(day.getAttribute("closed"))) {
        continue;
      }

      // Each day has operating times, e.g., "09:00 AM - 05:00 PM". The difference is computed.
      String textContent = day.getTextContent().trim();
      String[] times = textContent.split("-");
      if (times.length == 2) {
        String startStr = times[0].trim();
        String endStr = times[1].trim();
        LocalTime startTime = LocalTime.parse(startStr, formatter);
        LocalTime endTime = LocalTime.parse(endStr, formatter);
        double hours = (endTime.toSecondOfDay() - startTime.toSecondOfDay()) / 3600.0;
        totalOpenHours += hours;
      }
    }

    // A 'statistics' element is created to encapsulate the computed data for clarity and maintainability.
    Element statisticsEl = doc.createElement("statistics");

    // Average dish prices by type are inserted to convey cost insights.
    for (Map.Entry<String, Double> entry : typePriceMap.entrySet()) {
      double totalPrice = entry.getValue();
      int count = typeCountMap.get(entry.getKey());
      double avg = totalPrice / count;

      Element avgPriceEl = doc.createElement("averageDishPrice");
      avgPriceEl.setAttribute("type", entry.getKey());
      avgPriceEl.setAttribute("value", String.format("%.2f", avg));
      statisticsEl.appendChild(avgPriceEl);
    }

    // An overall average rating is included to reflect customer feedback.
    Element avgRatingEl = doc.createElement("averageRating");
    avgRatingEl.setTextContent(String.format("%.2f", averageRating));
    statisticsEl.appendChild(avgRatingEl);

    // A total ingredient count is provided to indicate the complexity of all dishes combined.
    Element ingredientCountEl = doc.createElement("ingredientCount");
    ingredientCountEl.setTextContent(String.valueOf(totalIngredients));
    statisticsEl.appendChild(ingredientCountEl);

    // Total operational hours for the week are inserted as a reference for scheduling and workload.
    Element totalOpenHoursEl = doc.createElement("totalOpenHours");
    totalOpenHoursEl.setTextContent(String.format("%.2f", totalOpenHours));
    statisticsEl.appendChild(totalOpenHoursEl);

    // The 'statistics' element is attached to the root, preserving all original data plus the new insights.
    Element root = doc.getDocumentElement();
    root.appendChild(statisticsEl);

    // The modified DOM is written to an output file so that computed statistics are persisted.
    TransformerFactory tFactory = TransformerFactory.newInstance();
    // External entity references are restricted to mitigate potential security vulnerabilities.
    tFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    tFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

    Transformer transformer;
    try {
      transformer = tFactory.newTransformer();
      transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
    } catch (TransformerConfigurationException e) {
      System.err.println("Could not create Transformer: " + e.getMessage());
      e.printStackTrace();
      return;
    }

    DOMSource source = new DOMSource(doc);
    StreamResult result = new StreamResult(new File("ex7-out.xml"));
    try {
      transformer.transform(source, result);
    } catch (TransformerException e) {
      System.err.println("XML transformation failed: " + e.getMessage());
      e.printStackTrace();
      return;
    }

    System.out.println("Document transformed successfully to ex7-out.xml.");
  }

  public static Document parseXMLWithXSD(String xmlPath, String xsdPath)
      throws SAXException, ParserConfigurationException, IOException {
    // A SchemaFactory is used to ensure that validation against the specified XSD is enforced during parsing.
    SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
    Schema schema = schemaFactory.newSchema(new File(xsdPath));

    // The DocumentBuilderFactory is configured to be namespace-aware and attach the schema for validation.
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    dbf.setSchema(schema);

    // Secure processing is enabled to defend against XML attacks (e.g., large entity expansions).
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

    // DOCTYPE declarations are disallowed, preventing XXE attacks that leverage external entities.
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

    // The DocumentBuilder is built with a secure and validating configuration.
    DocumentBuilder builder = dbf.newDocumentBuilder();

    // Parsing also triggers schema validation, ensuring the XML adheres to the defined structure.
    return builder.parse(new File(xmlPath));
  }
}
