/* Authored by iqbserve.de */
package iqb.jps.srvcomp;

import iqb.jps.JamnServer;

import iqb.jps.JamnServer.HttpHeader.Status;
import iqb.jps.JamnServer.MimeType;

import java.io.IOException;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.JamnServer.RequestMessage;
import iqb.jps.JamnServer.ResponseMessage;
import iqb.jps.core.HelperTool;
import iqb.jps.core.ResourceFileCache;
import iqb.jps.core.WebResourceRegistry;

/**
 * <pre>
 * The class realizes a simple Web Content Provider.
 * </pre>
 */
public class WebContentProvider implements JamnServer.ContentProvider, WebResourceRegistry {

    protected static HelperTool Tool = HelperTool.getInstance();
    protected static Logger LOG = LoggerFactory.getLogger(WebContentProvider.class);

    protected BiFunction<String, RequestMessage, String> pathMapper = (path, request) -> path;
    protected ResourceFileCache<WebFile> resourceCache = null;

    protected WebContentProvider() {
    }

    /**
     * Constructor for serving cached web content.
     */
    public WebContentProvider(ResourceFileCache<WebFile> webAppResourceCache) {
        this();
        resourceCache = webAppResourceCache;
    }

    /**
     * JamnServer.ContentProvider Interface method.
     */
    @Override
    public void setPathMapper(BiFunction<String, RequestMessage, String> mapper) {
        this.pathMapper = mapper;
    }

    /**
     * WebResourceRegistry Interface method.
     */
    @Override
    public WebResourceRegistry registerResource(String path, byte[] data) {
        resourceCache.registerResource(path, data);
        return this;
    }

    /**
     * JamnServer.ContentProvider Interface method.
     */
    @Override
    public void handleContentProcessing(RequestMessage request, ResponseMessage response) {

        // be gently by default
        response.setStatus(Status.SC_200_OK);

        WebFile webFile;
        try {
            if (request.isMethod("GET")) {
                webFile = getWebFile(request, response);

                doExtendedContentProcessing(webFile, request, response);

                if (!webFile.isEmpty()) {
                    response.writeToContent(webFile.getData());
                } else {
                    response.setStatus(Status.SC_204_NO_CONTENT);
                }
            } else {
                LOG.warn("WebContentProvider Warning: Unsupported HTTP Method [{}]", request.getMethod());
            }
        } catch (WebContentException ce) {
            LOG.debug("WebContentProvider Error:", ce);
            response.setStatus(ce.getHttpStatus());
        } catch (Exception e) {
            LOG.error("WebContentProvider internal Error GET [{}]", request.getPath(), e);
            response.setStatus(Status.SC_500_INTERNAL_ERROR);
        }
    }

    /**
     */
    protected WebFile getWebFile(RequestMessage request, ResponseMessage response)
            throws WebContentException {

        WebFile webFile = null;
        String decodedPath = request.getDecodedPath();
        decodedPath = pathMapper.apply(decodedPath, request);

        try {
            webFile = resourceCache.getResource(decodedPath);
            response.setContentType(webFile.getContentType());
        } catch (Exception e) {
            throw new WebContentException(Status.SC_404_NOT_FOUND,
                    String.format("Could NOT read file data [%s]", decodedPath), e);
        }

        return webFile;
    }

    /**
     * Interface for app internal use to get web files.
     * The requestPath is expected to be "/.../<filename>.<ext> e.g. /index.html".
     */
    public byte[] getWebFileData(String requestPath) throws IOException {
        WebFile webFile = resourceCache.getResource(requestPath);
        return webFile.getData();
    }

    /**
     * Factory method to create a WebFile object from a path and byte array.
     */
    public static WebFile newWebFile(String path, byte[] data) {
        WebFile webFile = new WebFile(path);
        webFile.setContentType(MimeType.getFromPath(path));
        webFile.setData(data);
        return webFile;
    }

    /**
    */
    protected void doExtendedContentProcessing(WebFile webFile, RequestMessage request,
            ResponseMessage response) {
        // do nothing by default
    }

    /*********************************************************
     * Provider classes and interfaces.
     *********************************************************/

    /**
     */
    public static class WebFile {
        protected String requestPath = "";
        protected String filePath = "";
        protected String contentType = "";
        protected byte[] data = new byte[0];

        public WebFile(String path) {
            requestPath = path;
        }

        public WebFile(byte[] data) {
            this.data = data;
        }

        public String getId() {
            return requestPath;
        }

        public boolean isEmpty() {
            return data.length == 0;
        }

        public String toString() {
            return requestPath;
        }

        public String getRequestPath() {
            return requestPath;
        }

        public void setRequestPath(String requestPath) {
            this.requestPath = requestPath;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        public boolean hasContentType(String type) {
            return this.contentType.equalsIgnoreCase(type);
        }
    }

    /**
     * Internal Exceptions thrown by this WebContentProvider
     */
    private static class WebContentException extends Exception {
        private static final long serialVersionUID = 1L;
        protected final String httpStatus;

        public WebContentException(String httpStatus, String msg, Throwable cause) {
            super(msg, cause);
            this.httpStatus = httpStatus;
        }

        public String getHttpStatus() {
            return httpStatus;
        }
    }
}
