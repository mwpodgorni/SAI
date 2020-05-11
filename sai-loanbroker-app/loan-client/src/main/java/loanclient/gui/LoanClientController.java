package loanclient.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import loanclient.model.LoanReply;
import loanclient.model.LoanRequest;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.Serializable;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;

public class LoanClientController implements Initializable, MessageListener {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @FXML
    private TextField tfSsn;
    @FXML
    private TextField tfAmount;
    @FXML
    private TextField tfTime;
    @FXML
    private ListView<ListViewLine> lvLoanRequestReply;

    LinkedHashMap<String, LoanRequest> storage = new LinkedHashMap<>(); //hashmap for matching request with message id
    Connection connection; //connect to the JMS
    Session session; //session for creating consumers
    Destination sendDestination; //reference to a queue destination
    Destination receiveDestination;
    ActiveMQConnectionFactory connectionFactory;
    private static String clientQueueName = "2222";
    private static String clientListenQueueName = "2121";
    private MessageProducer producer;
    private MessageConsumer responseConsumer;

    public LoanClientController() {

    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tfSsn.setText("123456");
        tfAmount.setText("80000");
        tfTime.setText("30");
        connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        connectionFactory.setTrustAllPackages(true);
    }

    @FXML
    public void btnSendLoanRequestClicked() {
        // create the BankInterestRequest
        int ssn = Integer.parseInt(tfSsn.getText());
        int amount = Integer.parseInt(tfAmount.getText());
        int time = Integer.parseInt(tfTime.getText());
        LoanRequest loanRequest = new LoanRequest(UUID.randomUUID().toString(), ssn, amount, time);


        //create the ListViewLine line with the request and add it to lvLoanRequestReply
        ListViewLine listViewLine = new ListViewLine(loanRequest);
        this.lvLoanRequestReply.getItems().add(listViewLine);

        // @TODO: send the loanRequest here...
        logger.info("Sent the loan request: " + loanRequest);
        try {
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            sendDestination = session.createQueue(clientQueueName);

            //setup a message producer to send message to the queue the server is consuming from
            producer = session.createProducer(sendDestination);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            // queue that client will listen for responses
//            receiveDestination = session.createTemporaryQueue();
            receiveDestination = session.createQueue(clientListenQueueName);
            //create a consumer that consumes message from temporary queue
            responseConsumer = session.createConsumer(receiveDestination);

            //handle messages to the temp queue
            responseConsumer.setMessageListener(this);

            //create message to send
//            Message request = session.createObjectMessage((Serializable) loanRequest);
//            TextMessage request = session.createTextMessage(loanRequest.toString());
            ObjectMessage request = session.createObjectMessage();
            request.setObject(loanRequest);
            //Set the reply to field to the queue the server responds to
            request.setJMSReplyTo(receiveDestination);
            this.producer.send(request);
            System.out.println("test:" + ((LoanRequest) request.getObject()).toString());
            //store id with request text
            storage.put(request.getJMSMessageID(), loanRequest);
        } catch (JMSException e) {
//            logger.info("error: "+e.toString());
        }
    }


    /**
     * This method returns the line of lvMessages which contains the given loan request.
     *
     * @param request BankInterestRequest for which the line of lvMessages should be found and returned
     * @return The ListViewLine line of lvMessages which contains the given request
     */
    private ListViewLine getRequestReply(LoanRequest request) {

        for (int i = 0; i < lvLoanRequestReply.getItems().size(); i++) {
            ListViewLine rr = lvLoanRequestReply.getItems().get(i);
            if (rr.getLoanRequest() != null && rr.getLoanRequest() == request) {
                return rr;
            }
        }

        return null;
    }

    @Override
    public void onMessage(Message message) {
        try {
            ObjectMessage object = (ObjectMessage) message;
            LoanReply clientMessage = (LoanReply) object.getObject();
            ListViewLine listViewLine = getRequestReply(storage.get(object.getJMSCorrelationID()));
            listViewLine.setLoanReply(clientMessage);
            Platform.runLater(() -> lvLoanRequestReply.refresh());

        } catch (JMSException e) {
//            logger.log(Level.WARNING, "Error: "+e.toString());
        }
    }
}
