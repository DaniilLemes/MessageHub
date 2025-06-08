package org.mh.messagehub;

import org.mh.messagehub.admin.KafkaTopicCreator;
import org.mh.messagehub.ui.ChatWindow;
import org.mh.messagehub.ui.LoginDialog;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.swing.*;

@SpringBootApplication
public class MessageHubApplication {

    public static void main(String[] args) {
        /* 1) upewnij się, że mamy brokera i topiki */
        String broker = System.getProperty("messagehub.broker", "localhost:9092");
        System.setProperty("messagehub.broker", broker);          // używany dalej przez KafkaConfig
        KafkaTopicCreator.createTopics();                         // tworzy chat-room, chat-private, chat-presence

        /* 2) start kontenera Spring (serwisy, konfiguracja) */
        SpringApplication app = new SpringApplication(MessageHubApplication.class);
        app.setHeadless(false);                                   // konieczne dla Swing
        app.run(args);

        /* 3) cała logika UI na EDT */
        SwingUtilities.invokeLater(() -> {
            /* ── login ───────────────────────────────────────────── */
            LoginDialog login = new LoginDialog(null);
            login.setVisible(true);

            if (!login.isSucceeded()) {                           // anulowano
                System.exit(0);
            }

            /* ── chat ────────────────────────────────────────────── */
            ChatWindow chat = new ChatWindow(
                    login.getNickname(),
                    login.getRoom()
            );
            chat.showWindow();                                    // metoda z ChatWindow – po prostu setVisible(true)
        });
    }
}
