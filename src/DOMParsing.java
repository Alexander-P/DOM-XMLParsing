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
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
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
    try {
      doc = parseXMLWithXSD(xmlPath, xsdPath);
      // If no exception is thrown, the XML is valid against the schema
      System.out.println("XML is valid and parsed successfully!");
      // Use 'doc' (the Document object) as needed...
    } catch (Exception e) {
      System.err.println("Validation/Parsing failed: " + e.getMessage());
      e.printStackTrace();
      return;
    }
    try {
      //    Collect data from the document
      //    XPath for more compact code.

      // --- Collect dish info: price by type ---
      XPathFactory xPathFactory = XPathFactory.newInstance();
      XPath xpath = xPathFactory.newXPath();
      NodeList dishNodes = (NodeList) xpath.evaluate("//dish", doc, XPathConstants.NODESET);

      // We will track total price and count per dish type to compute average
      Map<String, Double> typePriceMap = new HashMap<>();
      Map<String, Integer> typeCountMap = new HashMap<>();

      // Ingredient count
      int totalIngredients = 0;

      for (int i = 0; i < dishNodes.getLength(); i++) {
        Element dish = (Element) dishNodes.item(i);
        String type = dish.getAttribute("type");
        String priceStr = dish.getAttribute("price");
        double price = Double.parseDouble(priceStr);

        // Update type totals
        typePriceMap.put(type, typePriceMap.getOrDefault(type, 0.0) + price);
        typeCountMap.put(type, typeCountMap.getOrDefault(type, 0) + 1);

        // Count ingredients under <dish> -> <preparation_details> -> ... -> <ingredients>
        // NOTE: not all dishes have <preparation_details>, so be defensive
        NodeList ingredientsList = dish.getElementsByTagName("ingredient");
        totalIngredients += ingredientsList.getLength();
      }

      // --- Compute the average rating of all reviews ---
      NodeList reviewNodes = (NodeList) xpath.evaluate("//review", doc, XPathConstants.NODESET);
      double totalRating = 0.0;
      for (int i = 0; i < reviewNodes.getLength(); i++) {
        Element review = (Element) reviewNodes.item(i);
        double rating = Double.parseDouble(review.getAttribute("rating"));
        totalRating += rating;
      }
      double averageRating = (reviewNodes.getLength() > 0)
          ? (totalRating / reviewNodes.getLength()) : 0.0;

      // --- Compute total weekly open hours ---
      // We'll parse times in "hh:mm a" format or similar
      NodeList dayNodes = (NodeList) xpath.evaluate("//day", doc, XPathConstants.NODESET);
      double totalOpenHours = 0.0;
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");

      for (int i = 0; i < dayNodes.getLength(); i++) {
        Element day = (Element) dayNodes.item(i);
        // If closed="true", skip
        if ("true".equals(day.getAttribute("closed"))) {
          continue;
        }

        // The day text might be "09:00 AM - 05:00 PM"
        String textContent = day.getTextContent().trim();
        // Quick parse: split by '-'
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

      // 3) Create a new <statistics> element in the DOM
      Element statisticsEl = doc.createElement("statistics");

      // --- Add dish price averages by type ---
      for (Map.Entry<String,Double> entry : typePriceMap.entrySet()) {
        double totalPrice = entry.getValue();
        int count = typeCountMap.get(entry.getKey());
        double avg = totalPrice / count;

        Element avgPriceEl = doc.createElement("averageDishPrice");
        avgPriceEl.setAttribute("type", entry.getKey());
        avgPriceEl.setAttribute("value", String.format("%.2f", avg));
        statisticsEl.appendChild(avgPriceEl);
      }

      // --- Add average rating ---
      Element avgRatingEl = doc.createElement("averageRating");
      avgRatingEl.setTextContent(String.format("%.2f", averageRating));
      statisticsEl.appendChild(avgRatingEl);

      // --- Add total ingredient count ---
      Element ingredientCountEl = doc.createElement("ingredientCount");
      ingredientCountEl.setTextContent(String.valueOf(totalIngredients));
      statisticsEl.appendChild(ingredientCountEl);

      // --- Add total open hours ---
      Element totalOpenHoursEl = doc.createElement("totalOpenHours");
      totalOpenHoursEl.setTextContent(String.format("%.2f", totalOpenHours));
      statisticsEl.appendChild(totalOpenHoursEl);

      // 4) Append the <statistics> node as a child of <restaurant> (or wherever you prefer)
      Element root = doc.getDocumentElement();
      root.appendChild(statisticsEl);

      // 5) Write (transform) the DOM to a new file ex7-out.xml
      TransformerFactory tFactory = TransformerFactory.newInstance();
      tFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      tFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      Transformer transformer = tFactory.newTransformer();
      DOMSource source = new DOMSource(doc);
      StreamResult result = new StreamResult(new File("ex7-out.xml"));
      transformer.transform(source, result);

      System.out.println("Document transformed successfully to ex7-out.xml.");

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static Document parseXMLWithXSD(String xmlPath, String xsdPath) throws SAXException, ParserConfigurationException, IOException {
    // 1. Create a SchemaFactory for XSD validation
    SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
    // 2. Compile the schema from the XSD file
    Schema schema = schemaFactory.newSchema(new File(xsdPath));

    // 3. Create a DocumentBuilderFactory and set properties for validation
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setNamespaceAware(true);
    dbf.setSchema(schema);

    //This enables secure processing mode, preventing certain XML constructs (like large expansions or cycles) from causing excessive resource use.
    dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

    //This prevents DOCTYPE declarations, which in turn avoids many XXE attack vectors.
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);


    // 4. Create the DocumentBuilder using our configured factory
    DocumentBuilder builder = dbf.newDocumentBuilder();

    // 5. Parse the XML file (this will also validate against the XSD)
    return builder.parse(new File(xmlPath));
  }
}
