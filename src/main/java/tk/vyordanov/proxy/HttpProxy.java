package tk.vyordanov.proxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpProxy {

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.out.println("Usage: java tk.vyordanov.proxy.HttpProxy <sourceHost> <sourcePort> <targetHost> <targetPort>");
            return;
        }
        String sourceHost = args[0];
        String sourcePortStr = args[1];
        String targetHost = args[2];
        String targetPortStr = args[3];
        int sourcePort = Integer.parseInt(sourcePortStr);
        int targetPort = Integer.parseInt(targetPortStr);
        ExecutorService executor = Executors.newFixedThreadPool(6);

        HttpServer server = HttpServer.create(new InetSocketAddress(sourceHost, sourcePort), 0);
        server.createContext("/", new HttpsTargetProxyHandler(targetHost, targetPort));
        server.setExecutor(executor);
        server.start();
        System.out.println(" Server started on port " + sourcePort);
    }
}



class HttpsTargetProxyHandler implements HttpHandler {
    public static final String HTTPS_PROTOCOL = "https";
    private final String targetHost;
    private final int targetPort;

    public HttpsTargetProxyHandler(String targetHost, int targetPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    public void handle(HttpExchange httpExchange) throws IOException {
        try {
            URL targetUrl = new URL(HTTPS_PROTOCOL, targetHost, targetPort, httpExchange.getRequestURI().toString());
            HttpsURLConnection con = (HttpsURLConnection) targetUrl.openConnection();
            con.setRequestMethod(httpExchange.getRequestMethod());
            Headers headers = httpExchange.getRequestHeaders();
            for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                List<String> headerValues = header.getValue();
                StringBuilder builder = new StringBuilder();
                int size = headerValues.size();
                for (int i = 0; i < size; i++) {
                    builder.append(headerValues.get(i));
                    if (i < size - 1) {
                        builder.append(", ");
                    }
                }
                con.setRequestProperty(header.getKey(), builder.toString());
            }
            con.setDoOutput(true);
            con.setDoInput(true);

            InputStream sourceIs = httpExchange.getRequestBody();
            OutputStream targetOs = con.getOutputStream();
            ByteOutputStream bos = new ByteOutputStream();
            byte[] buf = new byte[1024];
            int len;
            while ((len = sourceIs.read(buf)) >= 0) {
                targetOs.write(buf, 0, len);
                bos.write(buf, 0, len);
            }
            String request = bos.toString();
            System.out.println("Request Body: ");
            System.out.println(request);
            System.out.println();

            int status = con.getResponseCode();
            if (status == 204) {
                httpExchange.sendResponseHeaders(status, -1);
            } else {
                httpExchange.sendResponseHeaders(status, 0);
            }
            Map<String, List<String>> respHeaders = con.getHeaderFields();
            Headers responseHeaders = httpExchange.getResponseHeaders();
            for (Map.Entry<String, List<String>> respHeader : respHeaders.entrySet()) {
                responseHeaders.put(respHeader.getKey(), respHeader.getValue());
            }
            InputStream targetIs = con.getInputStream();
            OutputStream sourceOs = httpExchange.getResponseBody();
            ByteOutputStream responseBos = new ByteOutputStream();

            while ((len = targetIs.read(buf)) >= 0) {
                sourceOs.write(buf, 0, len);
                responseBos.write(buf, 0, len);
            }
            String reponse = responseBos.toString();
            System.out.println("Response Body: ");
            System.out.println(reponse);
            System.out.println();

            targetOs.close();
            sourceOs.close();
            targetOs.close();
            sourceIs.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }
}