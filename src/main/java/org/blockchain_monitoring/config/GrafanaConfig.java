package org.blockchain_monitoring.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.blockchain_monitoring.fly_client_spring.FlyNet;
import org.blockchain_monitoring.model.MonitoringParams;
import org.blockchain_monitoring.model.grafana.dashboard.Dashboard;
import org.blockchain_monitoring.model.grafana.dashboard.Panel;
import org.blockchain_monitoring.model.grafana.dashboard.Row;
import org.blockchain_monitoring.model.grafana.dashboard.Target;
import org.blockchain_monitoring.model.grafana.datasource.Datasource;
import org.blockchain_monitoring.model.grafana.datasource.OrgPreferences;
import org.hyperledger.fabric.sdk.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.blockchain_monitoring.MonitoringConfiguration.DASHBOARD_TITLE;

public class GrafanaConfig {

    private static final Logger log = LoggerFactory.getLogger(GrafanaConfig.class);

    private final MonitoringParams monitoringParams;

    private final FlyNet flyNet;

    private ObjectMapper mapper = new ObjectMapper(new JsonFactory());

    @Autowired
    public GrafanaConfig(MonitoringParams monitoringParams, FlyNet flyNet) {
        this.monitoringParams = monitoringParams;
        this.flyNet = flyNet;
    }

    @PostConstruct
    public void init() {
        initGrafana();
    }

    private void initGrafana() {
        try {
            initDatasources();
        } catch (HttpClientErrorException e) {
            final String responseBodyAsString = e.getResponseBodyAsString();
            log.error(responseBodyAsString);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            initDashboards();
        } catch (HttpClientErrorException e) {
            final String responseBodyAsString = e.getResponseBodyAsString();
            log.error(responseBodyAsString);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        try {
            orgPreferences();
        } catch (HttpClientErrorException e) {
            final String responseBodyAsString = e.getResponseBodyAsString();
            log.error(responseBodyAsString);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void orgPreferences() throws IOException {
        log.info("start init grafana datasources");
        final File orgPreferencesTemplate = new File(monitoringParams.getOrgPreferencesGrafana());
        final OrgPreferences orgPreferences = mapper.readValue(orgPreferencesTemplate, OrgPreferences.class);


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Basic YWRtaW46YWRtaW4=");
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<OrgPreferences> request = new HttpEntity<>(orgPreferences, headers);

        final String orgPreferencesURL = monitoringParams.getUrlGrafana() + "/api/org/preferences";
        restTemplate.postForObject(orgPreferencesURL, request, String.class);
        log.info("finish init grafana OrgPreferences");
    }

    private void initDatasources() throws IOException {
        log.info("start init grafana datasources");
        final File datasourcesTemplate = new File(monitoringParams.getDatasourcesGrafana());
        final Datasource datasource = mapper.readValue(datasourcesTemplate, Datasource.class);


        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Basic YWRtaW46YWRtaW4=");
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Datasource> request = new HttpEntity<>(datasource, headers);

        final String datasourcesURL = monitoringParams.getUrlGrafana() + "/api/datasources";
        restTemplate.postForObject(datasourcesURL, request, String.class);
        log.info("finish init grafana datasources");
    }

    private void initDashboards() throws IOException {
        log.info("start init grafana dashboards");
        final File datasourcesTemplate = new File(monitoringParams.getDashboardsGrafana());
        final Dashboard dashboard = mapper.readValue(datasourcesTemplate, Dashboard.class);

        // TODO динамичая загрузка борды из flyNet
        dashboard.getDashboard().setTitle(DASHBOARD_TITLE);
        // Individual
        final Row statusRow = dashboard.getDashboard().getRows().get(0);
        final Row channelRow = dashboard.getDashboard().getRows().get(1);
        final Row chaincodeRow = dashboard.getDashboard().getRows().get(2);

        final List<Peer> allPeers = new ArrayList<>();
        flyNet.getOrganisations().forEach(organisation -> {
            final List<Peer> peers = organisation.getPeers();
            allPeers.addAll(peers);
        });
        try {
            fillStatusRow(statusRow, allPeers);
            fillChannelRow(channelRow, allPeers);
            fillChaincodeRow(chaincodeRow, allPeers);
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }

        // Common
//        final Row queryRow = dashboard.getDashboard().getRows().get(3);
//        final Row invokeRow = dashboard.getDashboard().getRows().get(4);
//        TODO add Event Listener
//        final Row eventHubRow = dashboard.getDashboard().getRows().get(5);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Authorization", "Basic YWRtaW46YWRtaW4=");
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Dashboard> request = new HttpEntity<>(dashboard, headers);

        final String dashboardsURL = monitoringParams.getUrlGrafana() + "/api/dashboards/db";
        restTemplate.postForObject(dashboardsURL, request, String.class);
        log.info("finish init grafana dashboards");
    }

    private void fillStatusRow(Row row, List<Peer> allPeers) throws CloneNotSupportedException {
        final String selectForPeer = "SELECT \"status\" FROM \"autogen\".\"%s\"";
        selectForPeers(row, allPeers, selectForPeer);
    }

    private void fillChannelRow(Row row, List<Peer> allPeers) throws CloneNotSupportedException {
        final String selectForPeer = "SELECT \"channel\" FROM \"autogen\".\"%s\" WHERE \"TAG_STATUS\" = 'UP'";
        selectForPeers(row, allPeers, selectForPeer);
    }

    private void fillChaincodeRow(Row row, List<Peer> allPeers) throws CloneNotSupportedException {
        final String selectForPeer = "SELECT \"chaincode\" FROM \"%s\" WHERE \"TAG_STATUS\" = 'UP'";
        selectForPeers(row, allPeers, selectForPeer);
    }

    private void selectForPeers(Row row, List<Peer> allPeers, String selectForPeer) throws CloneNotSupportedException {
        List<Panel> panels = new ArrayList<>();
        final List<Panel> panels1 = row.getPanels();
        int i = 0;
        for (Peer peer : allPeers) {
            final Panel templatePanel = panels1.get(0).clone();
            final Target templateTarget = templatePanel.getTargets().get(0).clone();
            templatePanel.setId(i);
            templateTarget.setMeasurement(peer.getName());
            final String query = String.format(selectForPeer, peer.getName());
            templateTarget.setQuery(query);
            templatePanel.setTargets(Collections.singletonList(templateTarget));
            templatePanel.setTitle(peer.getName());
            panels.add(templatePanel);
            i++;
        }
        row.setPanels(panels);
    }
}
