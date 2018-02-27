package juna;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MimeType;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.jetty.JettyWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

@SpringBootApplication
@ComponentScan("juna")
@EnableDynamoDBRepositories("juna")
public class JunaNotifier implements CommandLineRunner {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JunaNotifier.class);
    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        SpringApplication.run(JunaNotifier.class, args);
    }

    @Autowired
    private AmazonDynamoDB amazonDynamoDB;

    @Autowired
    private UserInfoRepository repository;
    
    @Autowired
    private EmailService emailService;

    @Override
    public void run(String... args) throws Exception {
        DynamoDBMapper mapper = new DynamoDBMapper(amazonDynamoDB);

        CreateTableRequest tableRequest = mapper.generateCreateTableRequest(UserInfo.class);
        tableRequest.setProvisionedThroughput(new ProvisionedThroughput(1L, 1L));
        
        TableUtils.createTableIfNotExists(amazonDynamoDB, tableRequest);
        
        new DigiTransitTrainsWebsocketClient(repository, emailService);
    }
    
    private static class DigiTransitTrainsWebsocketClient {
        
        public DigiTransitTrainsWebsocketClient(UserInfoRepository repository, EmailService emailService) {
            org.eclipse.jetty.websocket.client.WebSocketClient webSocketClient = new org.eclipse.jetty.websocket.client.WebSocketClient();
            webSocketClient.getPolicy().setMaxBinaryMessageSize(Integer.MAX_VALUE);
            webSocketClient.getPolicy().setMaxTextMessageSize(Integer.MAX_VALUE);
            JettyWebSocketClient jettyClient = new JettyWebSocketClient(webSocketClient);
            jettyClient.start();
            WebSocketTransport transport = new WebSocketTransport(jettyClient);
            List<Transport> transports = new ArrayList<>();
            transports.add(transport);
            WebSocketClient client = new SockJsClient(transports);
            WebSocketStompClient stompClient = new WebSocketStompClient(client);
            stompClient.setInboundMessageSizeLimit(Integer.MAX_VALUE);
            stompClient.setMessageConverter(
                    new MappingJackson2MessageConverter(new MimeType("application", "json", StandardCharsets.UTF_8),
                            new MimeType("text", "plain", StandardCharsets.UTF_8)) {

                    });
            // stompClient.setMessageConverter(new StringMessageConverter());
            StompSessionHandler sessionHandler = new MyStompSessionHandler(repository, emailService);

            stompClient.connect("http://rata.digitraffic.fi/api/v1/websockets/", sessionHandler);
        }
    }

    public static class MyStompSessionHandler extends StompSessionHandlerAdapter {
        
        private UserInfoRepository repository;
        private EmailService emailService;

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return List.class;
        }
        
        public MyStompSessionHandler(UserInfoRepository repository, EmailService emailService) {
            this.repository = repository;
            this.emailService = emailService;
        }
        
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
//            session.subscribe("/train-tracking/", this);
            session.subscribe("/live-trains/", this);
        }

        private void handleTrainTracking(Object payload) {
            System.out.println("train-tracking");
        }

        private static class UserInfoWithDifference {
            
            private int difference;
            private UserInfo userInfo;

            public UserInfoWithDifference(int difference, UserInfo info) {
                this.difference = difference;
                this.userInfo = info;
            }
        }
        
        private Map<Integer, List<UserInfoWithDifference>> interestingTrainIds() {
            Map<Integer, List<UserInfoWithDifference>> interesting = new HashMap<>();
            Iterable<UserInfo> info = repository.findAll();
            if (info != null) {
                Iterator<UserInfo> infoo = info.iterator();
                while (infoo.hasNext()) {
                    UserInfo i = infoo.next();
                    for (String trid : i.getTrainIds()) {
                        try {
                            int defaultDifference = 5;
                            int trainId;
                            String[] parts = trid.split(":");
                            if (parts.length == 1) {
                                // no difference specified
                                trainId = Integer.parseInt(parts[0]);
                            } else {
                                trainId = Integer.parseInt(parts[0]);
                                defaultDifference = Integer.parseInt(parts[1]);
                            }
                            List<UserInfoWithDifference> infos = interesting.get(trainId);
                            if (infos == null) {
                                infos = new ArrayList<>();
                                interesting.put(trainId, infos);
                            }
                            infos.add(new UserInfoWithDifference(defaultDifference, i));
                        } catch (Exception e) {
                            LOGGER.error("Could not add ", e);
                        }
                    }
                }
            }
            return interesting;
        }
        
        private void handleLiveTrains(Object payload) {
            Map<Integer, List<UserInfoWithDifference>> interesting = interestingTrainIds();
            Set<Integer> trainIds = interesting.keySet();
            List<Map<String, Object>> trains = (List<Map<String, Object>>) payload;
            trains = trains.stream().filter(tr -> trainIds.contains(tr.get("trainNumber"))).collect(Collectors.toList());
            for (Map<String, Object> train : trains) {
                boolean cancelled = (boolean) train.get("cancelled");
                int trainNumber = (int) train.get("trainNumber");
                if (cancelled) {
                    System.out.println("cancelled : " + train);
                } else {
                    List<Map<String, Object>> ttrs = (List<Map<String, Object>>) train.get("timeTableRows");
                    List<Map<String, Object>> ttrr = getLatestTimeTableRow(ttrs);
                    
                    Map<String, Object> actual = ttrr.get(0);
                    Map<String, Object> estimate = ttrr.get(1);
                    int actualDifference = 0;
                    int estimateDifference = 0;
                    if (actual != null) {
                        actualDifference = (int) actual.get("differenceInMinutes");
                    }
                    if (estimate != null) {
                        estimateDifference = (int) estimate.get("differenceInMinutes");
                    }
                    List<UserInfoWithDifference> differencesForTrain = interesting.get(trainNumber);
                    for (UserInfoWithDifference ifwd : differencesForTrain) {
                        int allowedDifference = ifwd.difference;
                        if (allowedDifference < actualDifference) {
                            // Ok, report late train
                            handleLateTrain(train, ifwd, actual, actualDifference, estimate, estimateDifference);
                        }
                    }
                }
            }
        }
        
        private List<Map<String, Object>> getLatestTimeTableRow(List<Map<String, Object>> timeTableRows) {
            Map<String, Object> latestTimeTableRow = null;
            Map<String, Object> nextEstimatedTimeTableRow = null;
            for (Map<String, Object> ttr : timeTableRows) {
                Object actualTime = ttr.get("actualTime");
                Object liveEstimateTime = ttr.get("liveEstimateTime");
                if (actualTime != null) {
                    if (latestTimeTableRow == null) {
                        latestTimeTableRow = ttr;
                    } else {
                        DateTime dtActualTiem = DateTime.parse((String)actualTime);
                        DateTime currentActualTime = DateTime.parse((String) latestTimeTableRow.get("actualTime"));
                        if (dtActualTiem.isAfter(currentActualTime)) {
                            latestTimeTableRow = ttr;
                        }
                    }
                }
                if (liveEstimateTime != null) {
                    if (nextEstimatedTimeTableRow == null) {
                        nextEstimatedTimeTableRow = ttr;
                    } else {
                        DateTime dtLiveEstimate = DateTime.parse((String)liveEstimateTime);
                        DateTime nextEstimateTime= DateTime.parse((String) nextEstimatedTimeTableRow.get("liveEstimateTime"));
                        if (nextEstimateTime.isAfter(dtLiveEstimate)) {
                            nextEstimatedTimeTableRow = ttr;
                        }
                    }
                }
            }
            List<Map<String, Object>> ttrPair = new ArrayList<>();
            ttrPair.add(latestTimeTableRow);
            ttrPair.add(nextEstimatedTimeTableRow);
            return ttrPair;
        }
        
        private void handleLateTrain(Map<String, Object> train, UserInfoWithDifference ifwd, Map<String, Object> actual, int actualDifference, Map<String, Object> estimate, int estimateDifference) {
            UserInfo info = ifwd.userInfo;
            String lineId = (String) train.get("commuterLineID");
            StringBuilder subject = new StringBuilder();
            subject.append("Train ").append(lineId).append(" (").append(train.get("trainNumber")).append(") is ").append(actualDifference).append(" minutes late @ ").append(actual.get("stationShortCode"));
            StringBuilder text = new StringBuilder();
            text.append("Train ").append(lineId).append(" (").append(train.get("trainNumber")).append(") is ").append(actualDifference).append(" minutes late which exceeds the given threshold ").append(ifwd.difference).append(". The causes are ").append(actual.get("causes"));
            emailService.sendSimpleMessage(info.getEmail(), subject.toString(), text.toString());
        }
        

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (headers.getDestination().equals("/live-trains/")) {
                handleLiveTrains(payload);
            } else if (headers.getDestination().equals("/train-tracking/")) {
                handleTrainTracking(payload);
            }
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload,Throwable exception) {
            exception.printStackTrace();
        }
        
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            exception.printStackTrace();
        }
    }
}
