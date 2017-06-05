package org.blockchain_monitoring.reports;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by Ruslan Kryukov on 01/06/2017.
 */
public class CrashReporter {
    private static String reportsURL;
    private static String userEmail;
    private static String userIP;
    private static boolean isReportsEnabled;
    private static ObjectMapper mapper = new ObjectMapper(new JsonFactory());
    private static HttpClient httpClient = HttpClientBuilder.create().build();

    public static void wrap(AppBody app) {
        try {
            loadProps();
            app.run();

        } catch (Exception ex) {
            if(isReportsEnabled) {
                ReportInfo reportInfo = new ReportInfo(ex, userEmail, userIP);
                try {
                    String json = mapper.writeValueAsString(reportInfo);
                    sendReport(json);
                } catch (Exception exx) {
                    exx.printStackTrace();
                }
            }
        }
    }

    private static void loadProps() {
        try (final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("monitoring.properties")) {
            Properties properties = new Properties();
            properties.load(stream);
            reportsURL = properties.getProperty("reports.url");
            userEmail = System.getenv("USER_EMAIL");
            userEmail = userEmail != null ? userEmail : "not_provided@email.null";
            String reportsDisabled = System.getenv("REPORTS_DISABLED");
            isReportsEnabled = reportsDisabled == null || reportsDisabled.toLowerCase().equals("false");
            try {
                userIP = IOUtils.toString(new URI("https://myexternalip.com/raw")).replace("\n", "");
            } catch (Exception ignore) {
                userIP = "127.0.0.1";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendReport(String report) throws Exception {
        HttpPost request = new HttpPost(reportsURL);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        builder.addTextBody("report", report, ContentType.APPLICATION_JSON);

        File packedLog = packLogs();

        if(packedLog != null) {
            builder.addBinaryBody("logfile", packedLog, ContentType.APPLICATION_OCTET_STREAM,
                    "monitoring.zip");
        }

        HttpEntity multipart = builder.build();
        request.setEntity(multipart);
        HttpResponse response = httpClient.execute(request);

        if(response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failure when tried to send report!");
        }
    }

    private static File packLogs() {
        try {
            File packedZipArchive = new File("/var/logs/fabric-monitoring/monitoring.log.zip");
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(packedZipArchive));
            out.setLevel(9);
            ZipEntry zipEntry = new ZipEntry("monitoring.log");
            out.putNextEntry(zipEntry);

            byte[] data = IOUtils.toString(
                    new FileInputStream(new File("/var/logs/fabric-monitoring/monitoring.log"))).getBytes();

            out.write(data, 0, data.length);
            out.closeEntry();
            out.close();

            return packedZipArchive;

        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
