package org.mh.messagehub.service;

public class PresenceEvent {
    private String action, nickname, room;
    private long timestamp;

    public String getAction() {
        return action;
    }

    public String getNickname() {
        return nickname;
    }

    public String getRoom() {
        return room;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}