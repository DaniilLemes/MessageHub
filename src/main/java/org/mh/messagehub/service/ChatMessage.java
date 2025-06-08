package org.mh.messagehub.service;

public class ChatMessage {
    private final String nickname;
    private final String message;
    private final long timestamp;

    public ChatMessage(String nickname, String message, long timestamp) {
        this.nickname = nickname;
        this.message = message;
        this.timestamp = timestamp;
    }

    public ChatMessage(String nickname, String message) {
        this(nickname, message, System.currentTimeMillis());
    }

    public String getNickname() {
        return nickname;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "nickname='" + nickname + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChatMessage that = (ChatMessage) o;

        if (timestamp != that.timestamp) return false;
        if (!nickname.equals(that.nickname)) return false;
        return message.equals(that.message);
    }

    @Override
    public int hashCode() {
        int result = nickname.hashCode();
        result = 31 * result + message.hashCode();
        result = 31 * result + Long.hashCode(timestamp);
        return result;
    }
}
