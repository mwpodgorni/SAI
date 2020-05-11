package loanbroker.gui;

import bank.model.BankInterestReply;
import bank.model.BankInterestRequest;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import loanclient.model.LoanReply;
import loanclient.model.LoanRequest;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.ResourceBundle;

public class LoanBrokerController implements Initializable {
    @FXML
    ListView<ListViewLine> lvLoanBrokerRequestReply;
    LinkedHashMap<String, ObjectMessage> storage = new LinkedHashMap<>(); //hashmap for matching request with message
    Connection connection; //connect to the JMS
    Session session; // session for creating consumers

    Destination clientDestination;
    MessageConsumer clientConsumer;
    MessageProducer clientProducer;

    Destination bankDestination;
    Destination brokerReceiveDestination;
    MessageConsumer bankConsumer;
    MessageProducer bankProducer;

    ActiveMQConnectionFactory connectionFactory;
    private static String clientQueueName = "2222";
    private static String bankQueueName = "abnRequestQueue";
    private static String brokerListenQueue = "3333";
    final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Started Loan Broker.");
        connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        connectionFactory.setTrustAllPackages(true);
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            clientDestination = session.createQueue(clientQueueName);

            //setup message consumer to get messages from producers
            clientConsumer = session.createConsumer(clientDestination);
            clientConsumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message msg) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ListViewLine clientRequest;
                                ObjectMessage object = (ObjectMessage) msg;
                                LoanRequest clientMessage = (LoanRequest) object.getObject();
                                System.out.println("M: "+clientMessage);
                                clientRequest = new ListViewLine(clientMessage);
                                lvLoanBrokerRequestReply.getItems().add(clientRequest);
                                //store request with message
                                storage.put(object.getJMSMessageID(), object);
                                System.out.println("client  id:"+object.getJMSMessageID());

                                //parse message to a bank
                                BankInterestRequest bankRequest = new BankInterestRequest(clientMessage.getId(), clientMessage.getAmount(), clientMessage.getTime());

                                //setup a message producer to send message to the bank queue
                                bankDestination= session.createQueue(bankQueueName);
                                bankProducer = session.createProducer(bankDestination);
                                bankProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

                                brokerReceiveDestination = session.createQueue(brokerListenQueue);
                                bankConsumer = session.createConsumer(brokerReceiveDestination);
                                bankConsumer.setMessageListener(new MessageListener() {
                                    @Override
                                    public void onMessage(Message message) {
                                        try{
                                                    ObjectMessage object1 = (ObjectMessage) message;
                                                    BankInterestReply bankMessage = (BankInterestReply) object1.getObject();
                                                    ListViewLine listViewLine = getRequestReply((LoanRequest) storage.get(object1.getJMSCorrelationID()).getObject());
                                                    listViewLine.setBankInterestReply(bankMessage);
                                                    Platform.runLater(() -> lvLoanBrokerRequestReply.refresh());

                                                    LoanReply loanReply = new LoanReply(bankMessage.getId(), bankMessage.getInterest(), bankMessage.getBankId());

                                                    ObjectMessage reply = session.createObjectMessage();
                                                    reply.setObject(loanReply);
                                                    reply.setJMSCorrelationID(object1.getJMSCorrelationID());

                                                    clientProducer = session.createProducer(null);
                                                    clientProducer.send(storage.get(object1.getJMSCorrelationID()).getJMSReplyTo(), reply);
                                                }catch(JMSException e){

                                                }
                                    }
                                });
                                ObjectMessage sendToBank = session.createObjectMessage();
                                sendToBank.setObject(bankRequest);
                                sendToBank.setJMSReplyTo(brokerReceiveDestination);
                                sendToBank.setJMSCorrelationID(object.getJMSMessageID());
                                bankProducer.send(sendToBank);
                                System.out.println("broker msg id:"+sendToBank.getJMSMessageID());



                            } catch (JMSException e) {
                                System.out.println("error: " + e.toString());
//                                logger.log(Level.WARNING, "Error: "+e.toString());
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
//            logger.log(Level.WARNING, "Error: "+e.toString());
        }
    }
    private ListViewLine getRequestReply(LoanRequest request) {

        for (int i = 0; i < lvLoanBrokerRequestReply.getItems().size(); i++) {
            ListViewLine rr = lvLoanBrokerRequestReply.getItems().get(i);
            if (rr.getLoanRequest() != null && rr.getLoanRequest() == request) {
                return rr;
            }
        }

        return null;
    }
}
