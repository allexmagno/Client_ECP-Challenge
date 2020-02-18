package main;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.representation.Form;
import org.w3c.dom.Element;
import utils.Convert;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.net.ssl.*;
import javax.ws.rs.core.MediaType;
import java.io.*;
import java.net.*;
import java.security.cert.X509Certificate;
import java.util.*;


public class Main {

    protected static final Logger log = LoggerFactory.getLogger(Main.class);
    private static boolean debug = false;

    public static boolean isDebug() {
        return debug;
    }

    public static void main(String[] args) {
        int qtdArgs = args.length;
        if ((qtdArgs == 3) || (qtdArgs == 4)) {

            //Verificando se foi digitado 4 argumentos e se o utlimo é referente a debug
            if (qtdArgs == 4) {
                String arg4 = args[3];
                if (arg4.compareToIgnoreCase("debug") == 0) {
                    debug = true;
                } else debug = false;
            }


            URL urlSp = null;
            URL urlIdp = null;

            //endpoint do SP que o Client deseja acessar
            String httpUrlSp = args[0];
            //Inserindo path do perfil ECP ao endpoint IdP SimpleSAMLphp
            String httpUrlIdp = args[1] + "/simplesaml/saml2/idp/SSOService.php";
	    if (debug) {
		log.debug("Endpoint do SP\n" + httpUrlSp);
		log.debug("Endpoint ECP do IdP\n" + httpUrlIdp);
	    }

            try {
                //Instanciando URLs
                urlIdp = new URL(httpUrlIdp);
                urlSp = new URL(httpUrlSp);

            } catch (MalformedURLException e) {
                log.error("Erro ao instanciar URL - " + e.toString());
            }


            //credenciais informadas como argumento - uuid - device
            String clientId = args[2];
            String serverId = "Alice";

            // Armazena os cookies recebidos durante as requisições.
            CookieManager cookieManager = new CookieManager();
            CookieHandler.setDefault(cookieManager);

            /*
            Primeira requisição do Client para o SP usando cabeçalho referente ao ECP - PAOS.
            Esperando resposta em uma mensagem formatada num envelope SOAP que o
            cabeçalho contenha um paos:Request e ecp:Request e o
            corpo contenha o samlp:AuthnRequest
            */
            HttpURLConnection conn = null;
            InputStream streamSpResponse = null;
            try {
                conn = (HttpURLConnection) urlSp.openConnection();
                conn.setRequestProperty("Accept", "text/html; application/vnd.paos+xml");
                conn.setRequestProperty("PAOS", "ver=\"urn:liberty:paos:2003-08\"; \"urn:oasis:names:tc:SAML:2.0:profiles:SSO:ecp\"");
                log.info("Enviando requisição ao SP - GET");
                streamSpResponse = conn.getInputStream();
            } catch (IOException e) {
                log.error("Erro ao executar primeira requisição ao SP - " + e.toString());
            }

            //Criando Node(xml) com Mensagem SOAP recebida do SP
            Node nodeSpResponse = Convert.getNode("/S:Envelope", streamSpResponse);
            conn.disconnect();


            String sp_response = Convert.toString(nodeSpResponse);
            if (debug) log.debug("----SP Response Body----\n " + sp_response);

            //Criando Node(xml) referente ao RelayState
            Node nodeRelayState = Convert.getNode("//ecp:RelayState", sp_response);
            String relayState = Convert.toString(nodeRelayState);
            if (debug) log.debug("----RelayState----\n " + relayState);


            //Criando Node(xml) referente ao endpoint do SP para tratar requisições ECP
            Node nodeResponseConsumer = Convert.getNode("//paos:Request", sp_response);
            String responseConsumer = nodeResponseConsumer.getAttributes().getNamedItem("responseConsumerURL").getNodeValue();
            if (debug) log.debug("----Response Consumer----\n " + responseConsumer);


            log.info("Montando requisição SOAP para IdP");
            //Criando Node com o corpo da mensagem SOAP recebida do SP
            Node nodeIdpRequest = nodeSpResponse.cloneNode(true);
            Node nodeHeader = nodeIdpRequest.getFirstChild();
            nodeIdpRequest.removeChild(nodeHeader);
            String idp_request = Convert.toString(nodeIdpRequest);
            if (debug) log.debug("----IdP Request Body----\n " + idp_request);

            String credentials = clientId + ":";
            String base64String = Base64.getEncoder().encodeToString(credentials.getBytes());

            /*
            Requisição do Client para o IdP usando metódo POST
            */
            HttpURLConnection connIdp = null;
            InputStream streamIdpResponse = null;
            try {
                connIdp = (HttpURLConnection) urlIdp.openConnection();
                connIdp.setDoOutput(true);
                connIdp.setInstanceFollowRedirects(Boolean.FALSE);
                connIdp.setRequestMethod("POST");
                connIdp.setRequestProperty("Accept", "text/xml;application/xml;text/html");
                connIdp.setRequestProperty("Accept-Encoding","deflate");
                connIdp.setRequestProperty("Content-Type", "text/xml");
                connIdp.setRequestProperty("Authorization", "Basic " + base64String);
                connIdp.setUseCaches( false );


                //Inserindo ecp:Request na requisição para o IdP
                connIdp.setDoOutput(true);
                OutputStreamWriter outputStreamWriter =
                        new OutputStreamWriter(connIdp.getOutputStream(), "UTF-8");
                outputStreamWriter.write(idp_request);
                outputStreamWriter.flush();
                log.info("Enviando requisição SOAP ao IdP - POST");
                streamIdpResponse = connIdp.getInputStream();
            } catch (ProtocolException e) {
                log.error("Erro ao setar Método POST da requisição ao IdP - " + e.toString());
            } catch (IOException e) {
                log.error("Erro ao executar requisição para o IdP - " + e.toString());
            }

            String redirect =  connIdp.getHeaderField("Location");
            connIdp.disconnect();




            //Iniciando requisições challenge-response
            /*
            Trecho do código a seguir foi inserido do ClientSamlPOST
            Disponível em https://github.com/dsubires/SAMLClient4IoT
            */

	    log.info("Preparando requisição ao IdP com redirect recebido da requisição anterior");
            ClientConfig config = new DefaultClientConfig();
            Client client = Client.create(config);
            client.setFollowRedirects(Boolean.FALSE);
            String cookieSimpleSAML = null;
            cookieSimpleSAML = cookieManager.getCookieStore().getCookies().get(0).getValue();
	    String idpResponseFinal = null;
	    
            try {

                //disableValidationSSL();

                /**
                 * GET IDP-logindevicepass -> 200
                 */
                URI redirectIdPtoIdP = new URI(redirect);
                URI endpoint = redirectIdPtoIdP;
                String authState = redirectIdPtoIdP.getQuery();
		log.info("Enviando requisição para solicitar URL do challenge");
                ClientResponse response = null;
                WebResource webResource = client.resource(endpoint);
                response = webResource
                        .accept(MediaType.APPLICATION_XHTML_XML_TYPE, MediaType.APPLICATION_XML_TYPE,
                                MediaType.TEXT_HTML_TYPE)
                        .header("Accept-Encoding", "deflate").header("Cookie", "SimpleSAML=" + cookieSimpleSAML)
                        .get(ClientResponse.class);

                /**
                 *
                 * POST IDP-logindevicepass -> 303
                 * POST with a blank password to generate the challenge
                 *
                 */



                authState = authState.substring(10);
                webResource = client.resource(endpoint.toString() + "?");


                Form formUser = new Form();
                formUser.add("username", clientId);
                formUser.add("password", "");
                formUser.add("AuthState", authState);

		log.info("Enviando requisição ao IdP para solicitar o challenge");
                response = webResource
                        .accept(MediaType.APPLICATION_XHTML_XML_TYPE, MediaType.APPLICATION_XML_TYPE,
                                MediaType.TEXT_HTML_TYPE)
                        .header("Accept-Encoding", "deflate").header("Cookie", "SimpleSAML=" + cookieSimpleSAML)
                        .post(ClientResponse.class, formUser);

                /**
                 *
                 * GET IDP-logindevicepass -> 200
                 * Get to the last redirect to access the login form with the challenge in the URL
                 *
                 */

                URI redirectAfterChallenge = response.getLocation();
                webResource = client.resource(redirectAfterChallenge);

                response = webResource
                        .accept(MediaType.APPLICATION_XHTML_XML_TYPE, MediaType.APPLICATION_XML_TYPE,
                                MediaType.TEXT_HTML_TYPE)
                        .header("Accept-Encoding", "deflate")
                        .header("Cookie", "SimpleSAML=" + cookieSimpleSAML).get(ClientResponse.class);
		log.info("Recuperando challenge da URL de redirecionamento");

                // look for the challenge in the URL
                String challenge = redirectAfterChallenge.getQuery();
                int foundChallenge = challenge.indexOf("challengeEncrypted");
                if (foundChallenge != -1)
                    challenge = challenge.substring(foundChallenge + 19, challenge.length() - 1);


                challenge = challenge.replace('|', ' ');
			
		if (debug) log.debug("--------Challenge----------\n " + challenge);

		log.info("Iniciando Aplicação em C para obter a resposta ao challenge");
                // Run the application in C to obtain the encrypted response of the challenge
                List<String> challengeList = new ArrayList<String>();
                challengeList.add("/home/ctgid/deviceauthentication/client");
                challengeList.add("1");
                challengeList.add(clientId);
                challengeList.add(serverId);
                String[] tempVector = challenge.split(" ");
                for (int i = 0; i < tempVector.length; i++) {
                    challengeList.add(tempVector[i]);
                }
		log.info("Processando resposta ao challenge...");
                Process process = new ProcessBuilder(challengeList).start();
                InputStream is = process.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                String response_ch = "";

                while ((line = br.readLine()) != null)
                    response_ch += line;
		

		if (debug) log.debug("--------Response----------\n " + response_ch);
		
                /**
                 *
                 * POST IDP-logindevicepass -> 200
                 * POST with user, password (challenge response) and AuthState
                 *
                 */
                webResource = client.resource(endpoint.toString());

		log.info("Enviando requisição ao IdP com resposta do challenge");
                Form formUserWithPasswd = new Form();
                formUserWithPasswd.add("username", clientId);
                formUserWithPasswd.add("password", response_ch);
                formUserWithPasswd.add("AuthState", authState);


                response = webResource
                        .accept(MediaType.APPLICATION_XHTML_XML_TYPE, MediaType.APPLICATION_XML_TYPE,
                                MediaType.TEXT_HTML_TYPE)
                        .header("Accept-Encoding", "deflate").header("Cookie", "SimpleSAML=" + cookieSimpleSAML)
                        .post(ClientResponse.class, formUserWithPasswd);


                /**
                 *
                 * From the IDP response we obtain the SAMLResponse and the appropriate Cookie
                 *
                 */

		
                idpResponseFinal = response.getEntity(String.class);
		idpResponseFinal = idpResponseFinal.replace("<?xml version=\"1.0\" encoding=\"utf-8\"?>","");
                if (debug) log.debug("-----------IdP Response-------------\n" + idpResponseFinal);

            }catch (Exception e){
                System.err.println(e.toString());
            }
	    /*
	    Fim do trecho do código inserido que executa o Challenge-Response	
            */ 
	    
	    
            //Criando Node(xml) referente a resposta da requisição ao IdP
            Node nodeIdpResponse = Convert.getNode("//SOAP-ENV:Envelope",idpResponseFinal);
            

            String idp_response = Convert.toString(nodeIdpResponse);
            if (debug) log.debug("----IdP Response Body----\n " + idp_response);

            //Montando requisição para o endpoint do SP que irá tratar o ecp:response
            Document docIdpResponse = nodeIdpResponse.getOwnerDocument();
            log.info("Montando Requisição para SP com resposta do IdP");
            Node nodeSpPackage = docIdpResponse.getDocumentElement();
            Node nodeRelay = docIdpResponse.importNode(nodeRelayState, true);

            //Trocando Node Header da mensagem SOAP recebida pelo IdP pelo Node RelayState
            Node nodeEcpResponse =
                    docIdpResponse.getElementsByTagName("SOAP-ENV:Header").item(0);
            Node nodenovo = docIdpResponse.createElement("SOAP-ENV:Header");
            nodenovo.appendChild(nodeRelay);

            nodeSpPackage.replaceChild(nodenovo, nodeEcpResponse);
            String sp_package = Convert.toString(nodeSpPackage);
            if (debug) log.debug("----SP Request Body----\n " + sp_package);

            /*
            Segunda requisição do Client para o SP usando metódo POST enviando no corpo
            mensagem SOAP recebida do IdP trocando o cabeçalho ecp:Response pelo
            ecp:RelayState que fora recebido do SP na primeira requisição.
            Espera receber o recurso do SP.
            */
            URL urlConsumerService;
            HttpURLConnection connSp = null;
            InputStream stream = null;
            try {
                urlConsumerService = new URL(responseConsumer);
                connSp = (HttpURLConnection) urlConsumerService.openConnection();
                connSp.setRequestMethod("POST");
                connSp.setRequestProperty("Content-Type", "application/vnd.paos+xml");

                connSp.setDoOutput(true);
                OutputStreamWriter out =
                        new OutputStreamWriter(connSp.getOutputStream(), "UTF-8");
                out.write(sp_package);
                out.flush();
                log.info("Enviando requisição ECP ao SP - POST");
                stream = connSp.getInputStream();

            } catch (MalformedURLException e) {
                log.error("Erro ao instanciar URL do Consumer Service SP - " + e.toString());
            } catch (ProtocolException e) {
                log.error("Erro ao setar Método POST da segunda requisição ao SP - " + e.toString());
            } catch (IOException e) {
                log.error("Erro ao executar segunda requisição ao SP - " + e.toString());
            }

            //Criando Node com resposta do SP que contém o recurso solicitado
            Node nodeFinalResponse = Convert.getNode("//body", stream);
            connSp.disconnect();
            String msgFinal = nodeFinalResponse.getTextContent();

            if (debug) log.debug("----SP Response Body----\n " + Convert.toString(nodeFinalResponse));
            else log.info("-----Mensagem Final----\n" + msgFinal);

        } else {
            System.err.println("Quantidade de argumentos inválida\n" +
                    "Uso:\n" +
                    "java -jar <arquivo.jar> <endpoint do SP> <endpoit do IdP> " +
                    "<usuario:senha> [OPCIONAL]");
        }
    }
        /**
         * Disable validation SSL. It allows access to SP and IDP even if they do not
         * have certificates signed by a valid entity.
         *
         * @throws Exception the exception
         */
        private static void disableValidationSSL() throws Exception {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            } };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        }



    }

