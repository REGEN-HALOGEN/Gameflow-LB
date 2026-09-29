package com.gameflow.model;

import java.io.Serializable;

/**
 * One entry in the operations event stream.
 */
public class SystemEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;               // EVT-000123
    private long timestamp;
    private String type;             // SESSION_CREATED, CIRCUIT_OPEN, ...
    private EventSeverity severity;
    private String message;
    private String serverId;         // nullable

    public SystemEvent() {
    }

    public SystemEvent(String id, long timestamp, String type,
                       EventSeverity severity, String message, String serverId) {
        this.id = id;
        this.timestamp = timestamp;
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.serverId = serverId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public EventSeverity getSeverity() { return severity; }
    public void setSeverity(EventSeverity severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
}
