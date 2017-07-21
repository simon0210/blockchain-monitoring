package org.blockchain_monitoring.model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hyperledger.fabric.protos.peer.Query;

public class PeerInfo {
    private final String name;
    private final List<Query.ChaincodeInfo> chaincodeInfoList;
    private final Set<String> channelList;
    private final PeerStatus status;
    private String chaincodes;
    private String channels;

    private PeerInfo(final String name,
                     final List<Query.ChaincodeInfo> chaincodeInfoList,
                     final Set<String> channelList,
                     final PeerStatus status) {
        this.name = name;
        this.chaincodeInfoList = chaincodeInfoList;
        this.channelList = channelList;
        this.status = status;
    }

    public static PeerInfo of(String name, List<Query.ChaincodeInfo> chaincodInfoList, Set<String> channelList, PeerStatus status) {
        return new PeerInfo(name, chaincodInfoList, channelList, status);
    }

    public String getName() {
        return name;
    }

    private List<Query.ChaincodeInfo> getChaincodeInfoList() {
        return chaincodeInfoList;
    }

    private Set<String> getChannelList() {
        return channelList;
    }

    public PeerStatus getStatus() {
        return status;
    }

    public String getChaincodes() {
        if (chaincodes == null && getChaincodeInfoList() != null) {
            final List<String> chancodeNameList = getChaincodeInfoList().stream().map(Query.ChaincodeInfo::getName)
                    .collect(Collectors.toList());

            final String strChaincodesList = chancodeNameList.toString();
            chaincodes = strChaincodesList.substring(1, strChaincodesList.length() - 1);
        } else if (getChaincodeInfoList() == null || getChaincodeInfoList().isEmpty()) {
            return "-";
        }
        return chaincodes;
    }

    public String getChannels() {
        if (channels == null && getChannelList() != null) {
            final String strChannelsList = getChannelList().toString();
            channels = strChannelsList.substring(1, strChannelsList.length() - 1);
        } else if (getChannelList() == null || getChannelList().isEmpty()) {
            return "-";
        }

        return channels;
    }
}
