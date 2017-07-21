package org.blockchain_monitoring.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.blockchain_monitoring.fly_client.FlyClient;
import org.blockchain_monitoring.fly_client_spring.FlyConfigService;
import org.blockchain_monitoring.fly_client_spring.FlyNet;
import org.blockchain_monitoring.fly_client_spring.model.config.Admin;
import org.blockchain_monitoring.model.PeerInfo;
import org.blockchain_monitoring.model.PeerStatus;
import org.blockchain_monitoring.service.MonitoringDB;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.Peer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrganisationMetricWriter implements Consumer<FlyNet.Organization> {

    private static final Logger log = LoggerFactory.getLogger(OrganisationMetricWriter.class);

    @Autowired
    public MonitoringDB monitoringDB;

    @Autowired
    public FlyConfigService flyConfigService;

    @Override
    public void accept(final FlyNet.Organization organization) {
        for (Peer peer : organization.getPeers()) {

            Optional<PeerInfo> peerInfo = getPeerInfo(peer, organization.getFlyClient());
            if (peerInfo.isPresent()) {
                monitoringDB.writePeerInfo(peerInfo.get());
            }
        }
    }

    private Optional<PeerInfo> getPeerInfo(final Peer peer, FlyClient flyClient) {
        try {
            final List<Query.ChaincodeInfo> chaincodeInfoList = new ArrayList<>();
            final Set<String> channelList = new HashSet<>();

            PeerStatus status;
            try {
                final Admin admin = flyConfigService.getAdmin(peer.getName());

                final Collection<Query.ChaincodeInfo> installedChaincodes = flyClient.queryInstalledChaincodes(admin.getLogin(), peer);
                chaincodeInfoList.addAll(installedChaincodes);
                final Set<String> channels = flyClient.queryChannels(peer);
                channelList.addAll(channels);
                status = PeerStatus.UP;
            } catch (Exception e) {
                log.error("PeerInfo: Peer [" + peer.getName() + "] is down");
                status = PeerStatus.DOWN;
            }

            return Optional.of(PeerInfo.of(peer.getName(), chaincodeInfoList, channelList, status));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return Optional.empty();
        }
    }
}
