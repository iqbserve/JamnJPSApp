package iqb.jps.wsoapi.base;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A simple general message data structure for web socket communication.
 */
public class WsoCommonMessage {

    public static final String SERVER_GLOBAL_REF = "server.global";

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_ERROR = "error";

    // header data
    protected String reference = "";
    protected String command = "";
    protected String functionModule = "";
    protected String argsSrc = "";
    protected String status = "";
    protected String error = "";
    // payload
    protected String bodydata = "";
    protected Map<String, String> attachments = new LinkedHashMap<>();

    public WsoCommonMessage() {
    }

    public WsoCommonMessage(String reference) {
        this.reference = reference;
    }

    @Override
    public String toString() {
        return String.join(", ", reference, command, functionModule);
    }

    public String getReference() {
        return reference;
    }

    public String getBodydata() {
        return bodydata;
    }

    public String getCommand() {
        return command;
    }

    public String getArgsSrc() {
        return argsSrc;
    }

    public String getFunctionModule() {
        return functionModule;
    }

    public String getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Map<String, String> getAttachments() {
        return attachments;
    }

    public WsoCommonMessage setReference(String reference) {
        this.reference = reference;
        return this;
    }

    public WsoCommonMessage setBodydata(String textdata) {
        this.bodydata = textdata;
        return this;
    }

    public WsoCommonMessage setCommand(String command) {
        this.command = command;
        return this;
    }

    public WsoCommonMessage setStatus(String status) {
        this.status = status;
        return this;
    }

    public WsoCommonMessage setSuccess(String textdata) {
        setStatus(STATUS_SUCCESS);
        this.bodydata = textdata;
        return this;
    }

    public WsoCommonMessage setError(String error) {
        setStatus(STATUS_ERROR);
        this.error = error;
        return this;
    }

    public WsoCommonMessage addAttachment(String key, String val) {
        this.attachments.put(key, val);
        return this;
    }
}