package utils;


import main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;


public abstract class Convert {

    protected static final Logger log = LoggerFactory.getLogger(Convert.class);

    public static Node getNode(String tag, InputStream inputStream) {

        InputSource source = new InputSource(inputStream);
        XPath xpath = XPathFactory.newInstance()
                .newXPath();
        xpath.setNamespaceContext(new NamespaceContextECP());

        Node node = null;
        try {
            node = (Node) xpath.evaluate(tag,source, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            log.error("Erro ao Converter InputStream para Node(xml) com tag " + tag + " - " + e.toString());
        }
        if(Main.isDebug()) log.debug("getNode(String tag, InputStream inputStream) - Convertendo inputStream para Node(xml): " + tag);
        return node;

    }
    public static Node getNode(String tag, String xml){

        InputSource source = new InputSource(new StringReader(xml));
        XPath xpath = XPathFactory.newInstance()
                .newXPath();
        xpath.setNamespaceContext(new NamespaceContextECP());

        Node node = null;
        try {
            node = (Node) xpath.evaluate(tag,source, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            log.error("Erro ao Converter String para Node(xml) com tag " + tag + " - " + e.toString());
        }
        if(Main.isDebug()) log.debug("getNode(String tag, String xml) - Convertendo String para Node(xml): " + tag);
        return node;

    }
    public static String toString(Node node){
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = null;
        StringWriter writer = null;
        try {
            transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            writer = new StringWriter();

            transformer.transform(new DOMSource(node), new StreamResult(writer));
        } catch (TransformerException e) {
            log.error("Erro ao Converter Node(xml) para String - " + e.toString());
        }
        if(Main.isDebug()) log.debug("toString(Node node) - Convertendo Node(xml) para string");
        return writer.toString();

    }

}
