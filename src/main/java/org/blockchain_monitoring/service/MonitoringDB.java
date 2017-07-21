package org.blockchain_monitoring.service;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.blockchain_monitoring.api.InfluxDestroyer;
import org.blockchain_monitoring.api.InfluxDestroyerException;
import org.blockchain_monitoring.api.InfluxSearcher;
import org.blockchain_monitoring.api.InfluxWriter;
import org.blockchain_monitoring.model.PeerInfo;
import org.influxdb.dto.Point;
import org.influxdb.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MonitoringDB {

    private static final Logger log = LoggerFactory.getLogger(MonitoringDB.class);

    @Autowired
    private InfluxWriter influxWriter;

    @Autowired
    private InfluxSearcher influxSearcher;

    @Autowired
    private InfluxDestroyer influxDestroyer;

    private static final String FIELD_CHAINCODE = "chaincode";
    private static final String FIELD_CHANNEL = "channel";
    private static final String FIELD_STATUS = "status";

    // PAY ATTENTION !!!
    // contract TAG name != FIELD name
    private static final String TAG_PEER = "TAG_PEER";
    private static final String TAG_STATUS = "TAG_STATUS";
    private static final String TAG_CHANNEL = "TAG_TAG_CHANNEL";
    private static final String TAG_CHAINCODE = "TAG_CHAINCODE";

    public void writePeerInfo(PeerInfo peerInfo) {
        log.debug("MonitoringDB.writePeerInfo");
        try {
            if (peerInfo.getName() == null || peerInfo.getName().isEmpty() || peerInfo.getStatus() == null) {
                throw new IllegalArgumentException("peerId and peerStatus can't be null");
            }
            log.debug("peerId = [" + peerInfo.getName() + "]," +
                    " chaincodeInfoList = [" + peerInfo.getChaincodes() + "]," +
                    " channelList = [" + peerInfo.getChannels() + "]," +
                    " peerStatus = [" + peerInfo.getStatus() + "]");

            final Point.Builder builder = Point.measurement(peerInfo.getName())
                    .addField(FIELD_STATUS, peerInfo.getStatus().ordinal());

            final HashMap<String, String> tags = new HashMap<String, String>() {{
                put(TAG_PEER, peerInfo.getName());
                put(TAG_STATUS, peerInfo.getStatus().name());
                put(TAG_CHANNEL, peerInfo.getChannels());
                put(TAG_CHAINCODE, peerInfo.getChaincodes());
            }};

            tags.entrySet().forEach(tag -> builder.tag(tag.getKey(), tag.getValue()));

            builder.addField(FIELD_CHAINCODE, peerInfo.getChaincodes());
            builder.addField(FIELD_CHANNEL, peerInfo.getChannels());

            final Point point = builder.build();
            updatePeerInfo(peerInfo.getName(), peerInfo, point);
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage(), e);
        }
    }

    private void updatePeerInfo(String peerId, PeerInfo peerInfo, Point point) {
        if (isNotExistsByPeerInfo(peerId, peerInfo)) {
            try {
                final HashMap<String, String> tags = new HashMap<String, String>() {{
                    put(TAG_PEER, peerInfo.getName());
                }};
                influxDestroyer.deleteMeasurementByTags(peerId, tags);
            } catch (InfluxDestroyerException e) {
                e.printStackTrace();
            }

            influxWriter.write(point);
        }
    }

    /**
     * Проверяем существуют ли значения в БД по peerId и status
     *
     * @param peerId - measurement
     * @param peerInfo - information about peer: status, chaincodeList, channelList
     * @return true - exists, false - not found
     */
    private boolean isNotExistsByPeerInfo(final String peerId, final PeerInfo peerInfo) {

        final Optional<QueryResult> queryOptional = influxSearcher
                .query("SELECT status FROM \"" + peerId +
                        "\" WHERE " +
                        "\"" + TAG_STATUS + "\" = '" + peerInfo.getStatus().name() + "' AND " +
                        "\"" + TAG_CHAINCODE + "\" = '" + peerInfo.getChaincodes() + "' AND " +
                        "\"" + TAG_PEER + "\" = '" + peerInfo.getName() +  "' AND " +
                        "\"" + TAG_CHANNEL + "\" = '" + peerInfo.getChannels() +         "' "
                );

        if(queryOptional.isPresent()) {
            QueryResult query = queryOptional.get();

            final List<QueryResult.Result> results = query.getResults();

            return results.isEmpty() || (results.size() == 1 && results.get(0).getSeries() == null);
        } else {
            return false;
        }
    }
}

