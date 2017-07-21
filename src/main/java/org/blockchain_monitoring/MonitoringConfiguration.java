package org.blockchain_monitoring;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import com.google.protobuf.InvalidProtocolBufferException;
import org.blockchain_monitoring.api.InfluxDestroyer;
import org.blockchain_monitoring.api.InfluxDestroyerException;
import org.blockchain_monitoring.api.InfluxSearcher;
import org.blockchain_monitoring.api.InfluxWriter;
import org.blockchain_monitoring.fly_client_spring.event.EventsProcessor;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.msp.Identities;
import org.hyperledger.fabric.protos.peer.FabricTransaction;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.BlockListener;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sun.security.x509.X500Name;

@Component
public class MonitoringConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MonitoringConfiguration.class);
    private static final String DATABASE_NAME = "hyperledger";
    public static final String DASHBOARD_TITLE = "Monitoring Hyperledger";
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String TRANSACTION_ID = "TRANSACTION_ID";
    private static final String VALIDATION_RESULT_CODE = "VALIDATION_RESULT_CODE";
    private static final String VALIDATION_RESULT_NAME = "VALIDATION_RESULT_NAME";
    private static final String ENDORSEMENTS = "ENDORSEMENTS";
    private static final String COMMON_BLOCK_EVENT_MEASUREMENT = "commonBlockEvent";

    private final InfluxWriter influxWriter;
    private final InfluxDestroyer influxDestroyer;

    private final InfluxSearcher influxSearcher;

    private final EventsProcessor eventsProcessor;


    @Value("${TIME_EVENT_LIFETIME:00:01:00}")
    private String envTimeEventLifetime;

    private LocalTime timeEventLifetime;

    @Autowired
    public MonitoringConfiguration(InfluxWriter influxWriter, InfluxSearcher influxSearcher, EventsProcessor eventsProcessor, InfluxDestroyer influxDestroyer) {
        this.influxSearcher = influxSearcher;
        this.influxWriter = influxWriter;
        this.eventsProcessor = eventsProcessor;
        this.influxDestroyer = influxDestroyer;
    }

    @PostConstruct
    public void init() {
        initInflux();
        initEventHandlers();
        // be careful that @Value init only after @PostConstruct not in Constructor
        timeEventLifetime = LocalTime.parse(envTimeEventLifetime);
    }

    private void initInflux() {
        influxWriter.createDatabase(DATABASE_NAME);
    }

    private void initEventHandlers() {
        BlockListener metricsEventListener = blockEvent -> {
            final BlockEvent.TransactionEvent next = blockEvent.getTransactionEvents().iterator().next();
            final FabricTransaction.TxValidationCode validationCode = FabricTransaction.TxValidationCode.forNumber(next.getValidationCode());

            List<String> endorsementsList;
            try {
                endorsementsList = FabricTransaction.ChaincodeActionPayload.parseFrom(FabricTransaction.Transaction.parseFrom(Common.Payload.parseFrom(
                        Common.Envelope.parseFrom(blockEvent.getBlock().getData().getData(0)).getPayload()).getData())
                        .getActions(0).getPayload()).getAction().getEndorsementsList().stream().map(endorsement -> {
                    try {
                        return Identities.SerializedIdentity.parseFrom(endorsement.getEndorser());
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).filter(Objects::nonNull)
                        .map(serializedIdentity -> {
                            final X509Certificate certificate;
                            String commonName = "";
                            try {
                                certificate = X509Certificate.getInstance(serializedIdentity.getIdBytes().toByteArray());
                                try {
                                        commonName = ((X500Name) certificate.getSubjectDN()).getCommonName();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            } catch (CertificateException e) {
                                e.printStackTrace();
                            }
                            return commonName;
                        }).collect(Collectors.toList());
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
                endorsementsList = Collections.emptyList();
            }

            final String transactionID = blockEvent.getTransactionEvents().iterator().next().getTransactionID();

            try {
                final LocalDateTime localDateTime = LocalDateTime.now().minusSeconds(timeEventLifetime.toSecondOfDay());
                influxDestroyer.deleteMeasurementOlderTime(blockEvent.getEventHub().getName(), localDateTime);
            } catch (InfluxDestroyerException e) {
                log.error(e.getMessage(), e);
            }

            writeCommonBlockEvent(blockEvent, validationCode, endorsementsList, transactionID);
            writeBlockEvent(blockEvent, validationCode, endorsementsList, transactionID);
        };

        eventsProcessor.addListener("metrics", metricsEventListener);
    }

    private void writeBlockEvent(BlockEvent blockEvent, FabricTransaction.TxValidationCode validationResult, List<String> endorsementsList, String transactionID) {
        Point point = null;
        try {
            point = Point.measurement(blockEvent.getEventHub().getName())
                    .tag(CHANNEL_ID, blockEvent.getChannelId())
                    .tag(TRANSACTION_ID, transactionID)
                    .addField(TRANSACTION_ID, transactionID)
                    .addField(VALIDATION_RESULT_NAME, validationResult.name())
                    .addField(VALIDATION_RESULT_CODE, validationResult.getNumber())
                    .addField(ENDORSEMENTS, endorsementsList.toString())
                    .build();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        influxWriter.write(point);
    }

    private synchronized void writeCommonBlockEvent(BlockEvent blockEvent, FabricTransaction.TxValidationCode validationResult, List<String> endorsementsList, String transactionID) {
        boolean isCommonBlockEventExists = influxSearcher.query("SELECT * FROM \"" + COMMON_BLOCK_EVENT_MEASUREMENT + "\" WHERE \"" + TRANSACTION_ID + "\" = '" + transactionID + "' AND \"" + VALIDATION_RESULT_CODE + "\" = '" + validationResult.getNumber() + "'").get().getResults().get(0).getSeries() == null;
        if (isCommonBlockEventExists) {
            Point commonPoint = null;
            try {
                commonPoint = Point.measurement(COMMON_BLOCK_EVENT_MEASUREMENT)
                        .addField(CHANNEL_ID, blockEvent.getChannelId())
                        .addField(TRANSACTION_ID, transactionID)
                        .addField(VALIDATION_RESULT_NAME, validationResult.name())
                        .addField(VALIDATION_RESULT_CODE, validationResult.getNumber())
                        .addField(ENDORSEMENTS, endorsementsList.toString())
                        .build();
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
            influxWriter.write(commonPoint);
        }
    }
}

