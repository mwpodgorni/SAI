package bank.gui;

import bank.model.BankInterestReply;
import bank.model.BankInterestRequest;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;

class BankController implements Initializable {

    private final String bankId;

    @SuppressWarnings("unused")
    @FXML
    public ListView<ListViewLine> lvBankRequestReply;
    @SuppressWarnings("unused")
    @FXML
    public TextField tfInterest;
    LinkedHashMap<BankInterestRequest, ObjectMessage> storage = new LinkedHashMap<>(); //hashmap for matching request with message
    Connection connection; //connect to the JMS
    Session session; // session for creating consumers
    Destination receiveDestination; //reference to a queue destination
    MessageConsumer consumer; // for receiving messages
    MessageProducer producer; // for sending messages
    ActiveMQConnectionFactory connectionFactory;
    private String bankQueueName;
    final Logger logger = LoggerFactory.getLogger(getClass());
    public BankController(String queueName, String bankId){
        this.bankId = bankId;
        bankQueueName=queueName;
        LoggerFactory.getLogger(getClass()).info("Created BankController with arguments [queueName="+queueName+"] and [bankId="+bankId+"]");
    }

    @SuppressWarnings("unused")
    @FXML
    public void btnSendBankInterestReplyClicked(){
        double interest = Double.parseDouble(tfInterest.getText());
        BankInterestReply bankInterestReply = new BankInterestReply(UUID.randomUUID().toString(), interest, bankId);

        ListViewLine listViewLine = lvBankRequestReply.getSelectionModel().getSelectedItem();
        if (listViewLine!= null){
         // @TODO send the bankInterestReply
            try {
                //set reply and update ui
                lvBankRequestReply.getSelectionModel().getSelectedItem().setBankInterestReply(bankInterestReply);
                Platform.runLater(() -> lvBankRequestReply.refresh());

                // create a message
                ObjectMessage response = session.createObjectMessage();
                response.setObject(bankInterestReply);
                response.setJMSCorrelationID(storage.get(listViewLine.getBankInterestRequest()).getJMSCorrelationID());

                producer = this.session.createProducer(null);
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                producer.send(storage.get(listViewLine.getBankInterestRequest()).getJMSReplyTo(), response);

            } catch (Exception e) {
//                logger.log(Level.WARNING, "Error: "+e.toString());
            }
        }

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        logger.info("Started Bank.");
        connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        connectionFactory.setTrustAllPackages(true);
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            receiveDestination = session.createQueue(bankQueueName);

            //setup message consumer to get messages from producers
            consumer = session.createConsumer(receiveDestination);
            consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message msg) {
                    // Update UI with received messages
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            ListViewLine request = null;
                            try {
                                ObjectMessage object = (ObjectMessage) msg;
                                BankInterestRequest message = (BankInterestRequest) object.getObject();
                                request= new ListViewLine(message);
                                //store request with message
                                storage.put(message, object);
                            } catch (JMSException e) {
//                                logger.log(Level.WARNING, "Error: "+e.toString());
                            }
                            lvBankRequestReply.getItems().add(request);
                        }
                    });
                }
            });

        } catch (Exception e) {
//            logger.log(Level.WARNING, "Error: "+e.toString());
        }

    }
}
