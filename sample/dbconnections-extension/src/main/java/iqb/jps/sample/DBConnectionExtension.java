/* Authored by iqbserve.de */
package iqb.jps.sample;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import iqb.jps.core.JsonTool;
import iqb.jps.annotation.WebService;
import iqb.jps.appcomp.ExtensionHandler.ExtensionInstanceContext;

/**
 */
public class DBConnectionExtension {
    protected static final String StatusOk = "ok";
    protected static final String StatusError = "error";

    protected ExtensionInstanceContext ctx;
    protected JsonTool jsonTool;
    protected Map<String, DbConnectionDef> connectionMap;
    protected Path dataFile;
    protected Charset encoding;

    public DBConnectionExtension(ExtensionInstanceContext ctx) throws IOException {
        this.ctx = ctx;
        this.dataFile = Paths.get(ctx.getDataPath().toString(), "db-connections.json");
        this.jsonTool = ctx.getJsonTool();
        this.encoding = ctx.getEncoding();
        this.loadConnections();
    }

    /********************************************************************************/
    /* API */
    /********************************************************************************/
    protected static final String apiRoot = "${jps.webservice.url.root}/service/";
    /**
     */
    @WebService(path = apiRoot + "get-db-connections")
    public DbConnectionResponse getDbConnections() {
        DbConnectionResponse response = new DbConnectionResponse();
        response.addAllConnections(connectionMap);
        return response.setStatusOk();
    }

    /**
     */
    @WebService(path = apiRoot + "save-db-connections")
    public DbConnectionResponse saveDbConnections(DbConnectionRequest request) {
        DbConnectionResponse response = new DbConnectionResponse();
        try {
            for (DbConnectionDef def : request.getConnections()) {
                connectionMap.put(def.getName(), def);
            }
            saveConnections();
        } catch (Exception e) {
            response.setStatusError(String.format("Failed to save DB Connections [%s]", e));
        }
        return response.setStatusOk();
    }

    /**
     */
    @WebService(path = apiRoot + "delete-db-connections")
    public DbConnectionResponse deleteDbConnections(DbConnectionRequest request) {
        DbConnectionResponse response = new DbConnectionResponse();
        try {
            for (DbConnectionDef def : request.getConnections()) {
                connectionMap.remove(def.getName());
            }
            saveConnections();
        } catch (IOException e) {
            response.setStatusError(String.format("Failed to save DB Connections [%s]", e));
        }
        return response.setStatusOk();
    }

    /********************************************************************************/
    /********************************************************************************/

    /**
     */
    protected void loadConnections() throws IOException {
        connectionMap = new LinkedHashMap<>();
        DbConnectionDef[] data;

        if (Files.exists(dataFile)) {
            String jsonStr = new String(Files.readAllBytes(dataFile), encoding);
            data = jsonTool.toObject(jsonStr, DbConnectionDef[].class);
            for (DbConnectionDef def : data) {
                connectionMap.put(def.getName(), def);
            }
        } else {
            createDemoData();
            saveConnections();
        }
    }

    /**
     */
    protected void saveConnections() throws IOException {
        Files.writeString(dataFile, jsonTool.toPrettyString(connectionMap.values()), encoding,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     */
    protected void createDemoData() {
        List<DbConnectionDef> defList = new ArrayList<>();
        defList.add(new DbConnectionDef()
                .setName("Oracle Test-Server")
                .setType("oracle")
                .setUrl("jdbc:oracle:thin:@TS-ORA:1521/XEPDB1")
                .setUser("admin"));
        defList.add(new DbConnectionDef()
                .setName("MySQL Development-Server")
                .setType("mysql")
                .setUrl("jdbc:mysql://DS-MSQL:3306/DVLPDB3")
                .setUser("devel"));
        for (DbConnectionDef def : defList) {
            connectionMap.put(def.getName(), def);
        }
    }

    /********************************************************************************/
    /* DATA TYPES */
    /********************************************************************************/

    /**
     */
    public static class DbConnectionRequest {
        private List<DbConnectionDef> connections = new ArrayList<>();

        public List<DbConnectionDef> getConnections() {
            return connections;
        }
    }

    /**
     */
    public static class DbConnectionResponse {
        private List<DbConnectionDef> connections = new ArrayList<>();
        private String status = "";
        private String error = "";

        private DbConnectionResponse setStatus(String status) {
            this.status = status;
            return this;
        }

        public DbConnectionResponse setStatusOk() {
            return setStatus(StatusOk);
        }

        public DbConnectionResponse setStatusError(String errorMsg) {
            this.error = errorMsg;
            return setStatus(StatusError);
        }

        public DbConnectionResponse addConnection(DbConnectionDef def) {
            this.connections.add(def);
            return this;
        }

        public DbConnectionResponse addAllConnections(Map<String, DbConnectionDef> connections) {
            this.connections.addAll(connections.values());
            return this;
        }

        public List<DbConnectionDef> getConnections() {
            return connections;
        }

        public String getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }
    }

    /**
     */
    public static class DbConnectionDef {
        protected String name;
        protected String type;
        protected String url;
        protected String user;
        protected String owner;

        public String getName() {
            return name;
        }

        public DbConnectionDef setName(String name) {
            this.name = name;
            return this;
        }

        public String getType() {
            return type;
        }

        public DbConnectionDef setType(String type) {
            this.type = type;
            return this;
        }

        public String getUrl() {
            return url;
        }

        public DbConnectionDef setUrl(String url) {
            this.url = url;
            return this;
        }

        public String getUser() {
            return user;
        }

        public DbConnectionDef setUser(String user) {
            this.user = user;
            return this;
        }

        public String getOwner() {
            return owner;
        }

        public DbConnectionDef setOwner(String owner) {
            this.owner = owner;
            return this;
        }
    }
}
