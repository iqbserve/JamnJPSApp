/* Authored by iqbserve.de */

package iqb.jps.srvcomp;

import iqb.jps.JamnServer.HttpHeader.FieldValue;
import iqb.jps.JamnServer.HttpHeader.Status;
import iqb.jps.annotation.WebService;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iqb.jps.JamnServer;
import iqb.jps.JamnServer.RequestMessage;
import iqb.jps.JamnServer.ResponseMessage;
import iqb.jps.core.JsonTool;
import iqb.jps.core.WebServiceRegistry;

/**
 * <pre>
 * The class realizes a simple Web Service Provider.
 * The current implementation supports get and post requests.
 * 
 * Where the java method signatures are expected to be:
 *  - post: <Object> method(<Object>) with <object> being convertible from and to json
 *  - get: <String> method(Map<String, String>) with the map containing the url parameter if any
 * 
 * </pre>
 */
public class WebServiceProvider implements JamnServer.ContentProvider, WebServiceRegistry {

    protected static Logger LOG = LoggerFactory.getLogger(WebServiceProvider.class);

    protected JsonTool jsonTool;
    protected UnaryOperator<String> placeholderResolver = text -> text;
    
    /**
     * A map holding all registered services.
     */
    protected ConcurrentHashMap<String, ServiceCartridge> serviceRegistry = new ConcurrentHashMap<>();

    /**
     */
    public WebServiceProvider setJsonTool(JsonTool tool) {
        jsonTool = tool;
        return this;
    }

    /**
     */
    public WebServiceProvider setPlaceholderResolver(UnaryOperator<String> resolver) {
        placeholderResolver = resolver;
        return this;
    }

    /**
     * <pre>
     * The public interface method to register and install Services.
     * </pre>
     */
    public WebServiceRegistry registerServices(Supplier<Object> instanceSupplier) {
        return registerServices(instanceSupplier.get(), instanceSupplier);
    }

    /**
     */
    protected WebServiceProvider registerServices(Object serviceInstance, Supplier<Object> instanceSupplier) throws WebServiceDefinitionException {
        ServiceCartridge serviceCart = null;
        WebService serviceAnno = null;
        Class<?> requestClass = null;
        Class<?> reponseClass = null;
        Class<?> serviceClass = serviceInstance.getClass();

        Method[] methodes = serviceClass.getDeclaredMethods();
        for (Method serviceMethod : methodes) {
            if (serviceMethod.isAnnotationPresent(WebService.class)) {
                serviceAnno = serviceMethod.getDeclaredAnnotation(WebService.class);
                checkServiceAnnotation(serviceAnno, serviceMethod);

                requestClass = getServiceRequestClassFrom(serviceMethod);
                reponseClass = getServiceResponseClassFrom(serviceMethod);

                serviceCart = new ServiceCartridge(serviceAnno, requestClass, reponseClass, serviceMethod,
                        jsonTool, instanceSupplier);
                serviceCart.path = placeholderResolver.apply(serviceCart.path);

                if (!serviceRegistry.containsKey(serviceCart.path)) {
                    serviceRegistry.put(serviceCart.path, serviceCart);
                    LOG.debug("WebService installed [{}] at [{}]", serviceCart.getName(), serviceCart.path);
                } else {
                    throw new WebServiceDefinitionException(
                            String.format("WebService Path of [%s] already defined for [%s]", serviceCart.getName(),
                                    serviceRegistry.get(serviceCart.path).getName()));
                }
            }
        }
        return this;
    }

    /**
     */
    public boolean isServicePath(String path) {
        return serviceRegistry.containsKey(path);
    }

    /**
     */
    public List<String> getAllServicePathNames() {
        List<String> names = new ArrayList<>(serviceRegistry.keySet());
        Collections.sort(names);
        return names;
    }

    /*********************************************************
     * Internal static helper methods.
     *********************************************************/
 
    /**
     */
    protected static String getServiceMethodName(Method method) {
        return method.getDeclaringClass().getSimpleName() + " - " + method.getName();
    }

    /**
     */
    protected static void checkServiceAnnotation(WebService serviceAnno, Method method)
            throws WebServiceDefinitionException {
        if (serviceAnno.path().isEmpty()) {
            throw new WebServiceDefinitionException(
                    String.format("No WebService path attribute found for [%s]", getServiceMethodName(method)));
        }
        if (serviceAnno.methods().length == 0) {
            throw new WebServiceDefinitionException(
                    String.format("No WebService methods attribute found for [%s]", getServiceMethodName(method)));
        }
    }

    /**
     */
    protected static Class<?> getServiceRequestClassFrom(Method method) throws WebServiceDefinitionException {
        Class<?>[] classes = method.getParameterTypes();
        if (classes.length == 1) {
            return classes[0];
        } else if (classes.length > 1) {
            throw new WebServiceDefinitionException(
                    String.format("WebService method must declare 0 or 1 parameter [%s]", getServiceMethodName(method)));
        }
        return null;
    }

    /**
     */
    protected static Class<?> getServiceResponseClassFrom(Method method) {
        return method.getReturnType();
    }

    /*********************************************************
     * The internal classes for loading and providing WebService objects.
     *********************************************************/
    /**
     * The internal class that holds a Service Instance.
     */
    protected static class ServiceCartridge {
        protected Map<String, String> httpMethods = HashMap.newHashMap(4);
        protected Supplier<Object> instanceSupplier = null;
        protected String path = "";
        protected String contentType = "";
        protected Class<?> requestClass = null;
        protected Class<?> responseClass = null;
        protected Method serviceMethod = null;
        protected boolean hasUrlParameterSignature = false;
        protected WebService serviceAnno = null;
        protected JsonTool json;

        protected ServiceCartridge(WebService serviceAnno, Class<?> requestClass,
                Class<?> responseClass, Method serviceMethod, JsonTool json, Supplier<Object> instanceSupplier) {
            this.json = json;
            this.path = serviceAnno.path().trim();
            this.contentType = serviceAnno.contentType().trim();
            this.requestClass = requestClass;
            this.responseClass = responseClass;
            this.serviceMethod = serviceMethod;
            this.instanceSupplier = instanceSupplier;
            this.serviceAnno = serviceAnno;


            // check for url parameter service
            if (requestClass != null && requestClass.isAssignableFrom(Map.class) && responseClass == String.class) {
                hasUrlParameterSignature = true;
            }

            for (String meth : serviceAnno.methods()) {
                httpMethods.put(meth.toUpperCase(), meth.toUpperCase());
            }
        }

        /**
         */
        @Override
        public String toString() {
            return getName();
        }

        /**
         */
        public String getName() {
            return getServiceMethodName(serviceMethod);
        }

        /**
         */
        public boolean isMethodSupported(String method) {
            return httpMethods.containsKey(method.toUpperCase());
        }

        /**
         */
        public boolean isContentTypeSupported(String contentType) {
            return (this.contentType.equalsIgnoreCase(contentType) || contentType.isEmpty());
        }

        /**
         */
        public String getContentType() {
            return contentType;
        }

        /**
         */
        public String[] getHeader(){
            return serviceAnno.header();
        }

        /**
         */
        public boolean hasParameter() {
            return (requestClass != null);
        }

        /**
         */
        public Object getInstance() {
            return instanceSupplier.get();
        }

        /**
         */
        protected Object callWith(String requestData, Map<String, String> requestParams)
                throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
            Object instance = getInstance();
            Object result = null;
            Object callArgument = null;

            if (hasUrlParameterSignature) {
                result = serviceMethod.invoke(instance, requestParams);
                return result;
            }

            if (getContentType().equalsIgnoreCase(FieldValue.APPLICATION_JSON)) {
                if (hasParameter()) {
                    callArgument = json.toObject(requestData, requestClass);
                    result = serviceMethod.invoke(instance, callArgument);
                } else {
                    result = serviceMethod.invoke(instance);
                }
                result = json.toString(result);
            } else if (getContentType().equalsIgnoreCase(FieldValue.TEXT_PLAIN)) {
                if (hasParameter() && requestClass == String.class) {
                    result = serviceMethod.invoke(instance, requestData);
                } else {
                    result = serviceMethod.invoke(instance);
                }
                if (responseClass == String.class) {
                    // if string defined return directly
                    return result;
                }
                // this surrounds a blank string with ""
                result = json.toString(result);
            }

            return result;
        }
    }

    /**
     * Exceptions thrown during Service execution.
     */
    protected static class WebServiceException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String httpStatus;

        WebServiceException(String httpStatus, String msg) {
            super(msg);
            this.httpStatus = httpStatus;
        }

        public String getHttpStatus() {
            return httpStatus;
        }
    }

    /*********************************************************
     * The public classes.
     *********************************************************/
    /**
     * Exceptions thrown during Service initialization/creation.
     */
    public static class WebServiceDefinitionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public WebServiceDefinitionException(String msg) {
            super(msg);
        }

        public WebServiceDefinitionException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /*********************************************************
     * The actual JamnServer.ContentProvider Interface Implementation.
     *********************************************************/
    @Override
    public void handleContentProcessing(RequestMessage request, ResponseMessage response) {

        ServiceCartridge serviceCart = null;
        Object resultObj = null;
        byte[] responseData = null;

        try {
            if (request.isMethod("GET") || request.isMethod("POST")) {
                serviceCart = getServiceFor(request.getDecodedPath(), request.getMethod(),
                        request.getContentType());

                resultObj = serviceCart.callWith(request.body(), request.getParameter());

                if (resultObj instanceof String result) {
                    responseData = result.getBytes();
                    response.setContentType(serviceCart.getContentType());
                    response.header().add(serviceCart.getHeader());
                    response.writeToContent(responseData);
                } else {
                    throw new WebServiceException(Status.SC_500_INTERNAL_ERROR,
                            String.format("Unsupported WebService API Return Type [%s] [%s]", resultObj.getClass(),
                                    serviceCart.getName()));
                }
                response.setStatus(Status.SC_200_OK);
            } else if (request.isMethod("OPTIONS")) {
                response.setStatus(Status.SC_204_NO_CONTENT);
            } else {
                response.setStatus(Status.SC_405_METHOD_NOT_ALLOWED);
            }
        } catch (WebServiceException wse) {
            LOG.debug("WebService API Error: [{}]", wse.getMessage());
            response.setStatus(wse.getHttpStatus());
        } catch (Exception e) {
            LOG.error("WebService internal/runtime error: [{}]", request.getDecodedPath(), e);
            response.setStatus(Status.SC_500_INTERNAL_ERROR);
        }
    }

    /**
     */
    protected ServiceCartridge getServiceFor(String path, String method, String contentType)
            throws WebServiceException {
        ServiceCartridge service = null;
        if (serviceRegistry.containsKey(path)) {
            service = serviceRegistry.get(path);

            if (!service.isMethodSupported(method)) {
                throw new WebServiceException(Status.SC_405_METHOD_NOT_ALLOWED,
                        String.format("Unsupported WebService Method [%s] [%s]", method, service.getName()));
            }
            if (!service.isContentTypeSupported(contentType)) {
                throw new WebServiceException(Status.SC_400_BAD_REQUEST, String
                        .format("Unsupported WebService ContentType [%s] [%s]", contentType, service.getName()));
            }
        } else {
            throw new WebServiceException(Status.SC_404_NOT_FOUND,
                    String.format("Unsupported WebService Path [%s]", path));
        }
        return service;
    }
}
