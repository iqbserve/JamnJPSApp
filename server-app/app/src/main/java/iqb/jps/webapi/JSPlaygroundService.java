/* Authored by iqbserve.de */
package iqb.jps.webapi;

import java.util.HashMap;
import java.util.Map;

import iqb.jps.annotation.WebService;

/**
 */
public class JSPlaygroundService {
    private static final String StatusOk = "ok";
    private static final String StatusError = "error";

    private Map<String, PlaygroundRequest> contentBuffer = new HashMap<>();

    private static final JSPlaygroundService instance = new JSPlaygroundService();
    public static JSPlaygroundService getInstance() {
        return instance;
    }
    private JSPlaygroundService() {
    }

    /********************************************************************************/
    /* API */
    /********************************************************************************/
    private static final String apiRoot = "${jps.webservice.url.root}/service/";

    /**
     */
    @WebService(path = apiRoot + "upload-playground-content")
    public PlaygroundResponse uploadPlaygroundScript(PlaygroundRequest request) {
        PlaygroundResponse response = new PlaygroundResponse();

        contentBuffer.put(request.getContentId(), request);
        return response.setStatusOk();
    }

    /**
     */
    @WebService(path = "/vres/playground/playground-run-code.mjs", methods = {"GET"}, contentType = "text/javascript", header = {"Cache-Control: no-store, must-revalidate"})
    public String getBufferedScript(Map<String, String> params) {
        PlaygroundRequest request = contentBuffer.remove(params.get("id"));
        String script = "";

        if (request != null) {
            script = request.getContent().get("mjs");
        }

        return script;
    }

    /********************************************************************************/
    /********************************************************************************/

    /********************************************************************************/
    /* DATA TYPES */
    /********************************************************************************/
    /**
     */
    public static class PlaygroundRequest {
        private String clientId = "";
        private String contentId = "";
        private boolean keep = false;

        private Map<String, String> content = new HashMap<>();

        public String getClientId() {
            return clientId;
        }

        public String getContentId() {
            return contentId;
        }

        public Map<String, String> getContent() {
            return content;
        }

        public boolean isKeep() {
            return keep;
        }
    }

    /**
     */
    public static class PlaygroundResponse {
        protected String status = "";
        protected String error = "";

        private PlaygroundResponse setStatus(String status) {
            this.status = status;
            return this;
        }

        public PlaygroundResponse setStatusOk() {
            return setStatus(StatusOk);
        }

        public PlaygroundResponse setStatusError(String errorMsg) {
            this.error = errorMsg;
            return setStatus(StatusError);
        }
    }

}
