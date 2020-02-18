package utils;

import javax.xml.namespace.NamespaceContext;
import java.util.Iterator;


public class NamespaceContextECP implements NamespaceContext {
    @Override
    public String getNamespaceURI(String s) {
        if ("ecp".equals(s)) {
            return "urn:oasis:names:tc:SAML:2.0:profiles:SSO:ecp";
        }
        else if ("S".equals(s) || "SOAP-ENV".equals(s)){
            return "http://schemas.xmlsoap.org/soap/envelope/";
        }
        else if ("paos".equals(s)){
            return "urn:liberty:paos:2003-08";
        }
        else if ("samlp".equals(s)){
            return "urn:oasis:names:tc:SAML:2.0:protocol";
        }
        else if ("saml".equals(s)){
            return "urn:oasis:names:tc:SAML:2.0:assertion";
        }
        return null;
    }

    @Override
    public String getPrefix(String s) {
        return null;
    }

    @Override
    public Iterator<String> getPrefixes(String s) {
        return null;
    }
}
