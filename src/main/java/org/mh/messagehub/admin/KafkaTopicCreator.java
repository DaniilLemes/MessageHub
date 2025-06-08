package org.mh.messagehub.admin;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.Arrays;
import java.util.Properties;

public class KafkaTopicCreator {
    public static void createTopics() {
        String bootstrapServers = System.getProperty("messagehub.broker", "localhost:9092");
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(props)) {
            NewTopic chatRoom = new NewTopic("chat-room", 3, (short) 1);
            NewTopic chatPrivate = new NewTopic("chat-private", 3, (short) 1);
            NewTopic chatPresence = new NewTopic("chat-presence", 3, (short) 1);

            admin.createTopics(
                    Arrays.asList(chatRoom, chatPrivate, chatPresence)
            ).all().get();
            System.out.println("Tematy utworzone poprawnie.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
