package org.mh.messagehub.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.mh.messagehub.config.KafkaConfig;
import org.mh.messagehub.service.Message;
import org.mh.messagehub.service.PresenceEvent;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jedno okno czatu (pokój). 100 % lokalne – jedyny transport to Kafka.
 * <p>
 *  ‣ publiczne wiadomości      → topic "chat-room"     (klucz = "public")
 *  ‣ prywatne wiadomości       → topic "chat-private"  (klucz = <nick-odbiorcy>)
 *  ‣ sygnały obecności JOIN/LEAVE → topic "chat-presence" (klucz = <nick-nadawcy>)
 */
public class ChatWindow extends JFrame {

    /* ---- dane stałe / pomocnicze -------------------------------------- */
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault());

    /* ---- pola konfiguracyjne ------------------------------------------ */
    private final String nickname;
    private final String room;

    /* ---- kafka --------------------------------------------------------- */
    private final Producer<String, String> producer;
    private final Consumer<String, String> consumer;
    private volatile boolean running = true;

    /* ---- model UI ------------------------------------------------------ */
    private final JTextArea chatArea = new JTextArea();
    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final JComboBox<String> recipientCombo = new JComboBox<>();
    private final Set<String> currentUsers = ConcurrentHashMap.newKeySet();

    /* ------------------------------------------------------------------- */
    public ChatWindow(String nickname, String room) {
        super("MessageHub – pokój: " + room + "   |   użytkownik: " + nickname);
        this.nickname = nickname;
        this.room = room;

        /* 1) Połączenie z brokerem -------------------------------------- */
        this.producer = KafkaConfig.createProducer();
        this.consumer = KafkaConfig.createConsumer(room, nickname);

        /* 2) GUI --------------------------------------------------------- */
        initUI();

        /* 3) Subskrypcja topiców ---------------------------------------- */
        consumer.subscribe(Arrays.asList("chat-presence", "chat-room", "chat-private"));

        /* 4) Powiadom o dołączeniu i wystartuj pętlę konsumenta --------- */
        sendPresence("JOIN");
        startConsumerLoop();
    }

    /* =============== BUDOWANIE GUI ===================================== */
    private void initUI() {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(700, 450);
        setLocationRelativeTo(null);

        /* lewa kolumna: lista użytkowników */
        JList<String> userList = new JList<>(userListModel);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(150, 0));

        /* środek: log rozmowy */
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        /* dół: input + odbiorca + przycisk */
        JTextField input = new JTextField();
        JButton sendBtn = new JButton("Wyślij");

        JPanel south = new JPanel(new BorderLayout(5, 5));
        recipientCombo.addItem("Wszyscy");
        south.add(recipientCombo, BorderLayout.WEST);
        south.add(input, BorderLayout.CENTER);
        south.add(sendBtn, BorderLayout.EAST);

        /* złożenie okna */
        JPanel main = new JPanel(new BorderLayout());
        main.add(userScroll, BorderLayout.WEST);
        main.add(chatScroll, BorderLayout.CENTER);
        main.add(south, BorderLayout.SOUTH);
        setContentPane(main);

        /* akcje */
        sendBtn.addActionListener(ev -> sendMessage(input));
        input.addActionListener(ev -> sendMessage(input));

        /* aktualizacja listy odbiorców */
        userListModel.addListDataListener(new ListDataListener() {
            private void refresh() {
                recipientCombo.removeAllItems();
                recipientCombo.addItem("Wszyscy");
                for (int i = 0; i < userListModel.size(); i++) {
                    String u = userListModel.getElementAt(i);
                    if (!u.equals(nickname)) recipientCombo.addItem(u);
                }
            }
            @Override public void intervalAdded(ListDataEvent e) { refresh(); }
            @Override public void intervalRemoved(ListDataEvent e) { refresh(); }
            @Override public void contentsChanged(ListDataEvent e) { refresh(); }
        });

        /* obsługa zamknięcia */
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { shutdown(); }
        });
    }

    /* =============== WYSYŁANIE WIADOMOŚCI ============================== */
    private void sendMessage(JTextField input) {
        String text = input.getText().trim();
        if (text.isEmpty()) return;

        String recipient = (String) recipientCombo.getSelectedItem();
        boolean isPublic = recipient == null || "Wszyscy".equals(recipient);

        Message msg = new Message();
        msg.setSender(nickname);
        msg.setRoom(room);
        msg.setText(text);
        msg.setTimestamp(System.currentTimeMillis());

        if (isPublic) {
            msg.setType("public");
            sendToKafka("chat-room", "public", msg);
            appendOwnPublic(msg);
        } else {
            msg.setType("private");
            msg.setRecipient(recipient);
            sendToKafka("chat-private", recipient, msg);
            appendOwnPrivate(msg);
        }
        input.setText("");
    }

    private void sendToKafka(String topic, String key, Message msg) {
        try {
            String json = MAPPER.writeValueAsString(msg);
            producer.send(new ProducerRecord<>(topic, key, json), (meta, ex) -> {
                if (ex != null) ex.printStackTrace();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* =============== PĘTLA KONSUMENTA ================================== */
    private void startConsumerLoop() {
        Thread th = new Thread(() -> {
            try {
                while (running) {
                    ConsumerRecords<String, String> batch = consumer.poll(Duration.ofMillis(300));
                    for (ConsumerRecord<String, String> rec : batch) processRecord(rec);
                }
            } catch (WakeupException ignore) {
            } finally {
                consumer.close();
            }
        }, "chat-consumer-" + nickname);
        th.setDaemon(true);
        th.start();
    }

    private void processRecord(ConsumerRecord<String, String> rec) {
        try {
            switch (rec.topic()) {
                case "chat-presence":
                    handlePresence(rec.value());
                    break;
                case "chat-room":
                    handlePublic(rec.value());
                    break;
                case "chat-private":
                    handlePrivate(rec.value());
                    break;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /* -------- presence ------------- */
    private void handlePresence(String json) throws Exception {
        PresenceEvent ev = MAPPER.readValue(json, PresenceEvent.class);
        if (!room.equals(ev.getRoom())) return;

        SwingUtilities.invokeLater(() -> {
            String action = ev.getAction();
            if ("JOIN".equals(action)) {
                if (currentUsers.add(ev.getNickname())) {
                    userListModel.addElement(ev.getNickname());
                    appendSystem(ev.getNickname() + " dołączył(a) do pokoju");
                }
            } else if ("LEAVE".equals(action)) {
                if (currentUsers.remove(ev.getNickname())) {
                    userListModel.removeElement(ev.getNickname());
                    appendSystem(ev.getNickname() + " opuścił(a) pokój");
                }
            }
        });
    }

    /* -------- public msg ----------- */
    private void handlePublic(String json) throws Exception {
        Message m = MAPPER.readValue(json, Message.class);
        if (!room.equals(m.getRoom()) || !"public".equals(m.getType())) return;
        SwingUtilities.invokeLater(() -> appendPublic(m));
    }

    /* -------- private msg ---------- */
    private void handlePrivate(String json) throws Exception {
        Message m = MAPPER.readValue(json, Message.class);
        if (!room.equals(m.getRoom()) || !"private".equals(m.getType())) return;
        if (!nickname.equals(m.getRecipient()) && !nickname.equals(m.getSender())) return;
        SwingUtilities.invokeLater(() -> appendPrivate(m));
    }

    /* =============== FORMATOWANIE I DODAWANIE ========================== */
    private void appendSystem(String txt) {
        chatArea.append("[SYSTEM] " + txt + "\n");
    }

    private void appendPublic(Message m) {
        chatArea.append("[" + TIME_FMT.format(Instant.ofEpochMilli(m.getTimestamp())) + "] "
                + m.getSender() + ": " + m.getText() + "\n");
    }

    private void appendOwnPublic(Message m) {
        chatArea.append("[" + TIME_FMT.format(Instant.ofEpochMilli(m.getTimestamp())) + "] Ty → wszyscy: "
                + m.getText() + "\n");
    }

    private void appendPrivate(Message m) {
        chatArea.append("[PRIVATE] [" + TIME_FMT.format(Instant.ofEpochMilli(m.getTimestamp())) + "] "
                + m.getSender() + " → " + m.getRecipient() + ": " + m.getText() + "\n");
    }

    private void appendOwnPrivate(Message m) {
        chatArea.append("[PRIVATE] [" + TIME_FMT.format(Instant.ofEpochMilli(m.getTimestamp())) + "] Ty → "
                + m.getRecipient() + ": " + m.getText() + "\n");
    }

    /* =============== SHUTDOWN ========================================= */
    private void sendPresence(String action) {
        try {
            PresenceEvent ev = new PresenceEvent();
            ev.setAction(action);
            ev.setNickname(nickname);
            ev.setRoom(room);
            ev.setTimestamp(System.currentTimeMillis());
            String json = MAPPER.writeValueAsString(ev);
            producer.send(new ProducerRecord<>("chat-presence", nickname, json));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void shutdown() {
        sendPresence("LEAVE");
        running = false;
        consumer.wakeup();
        producer.close();
        dispose();
    }

    /* =============== API ZEWNĘTRZNE ==================================== */
    public void showWindow() { SwingUtilities.invokeLater(() -> setVisible(true)); }
}
