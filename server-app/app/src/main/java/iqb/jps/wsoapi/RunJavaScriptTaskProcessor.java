/* Authored by iqbserve.de */
package iqb.jps.wsoapi;

import iqb.jps.appcomp.JavaScriptProvider;
import iqb.jps.appcomp.JavaScriptProvider.JSCallContext;
import iqb.jps.core.HelperTool;
import iqb.jps.wsoapi.base.WsoCommonMessage;
import iqb.jps.wsoapi.base.WsoContext;
import iqb.jps.wsoapi.base.WsoTaskProcessor;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunJavaScriptTaskProcessor implements WsoTaskProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RunJavaScriptTaskProcessor.class);
    private static final HelperTool Tool = HelperTool.getInstance();

    private final Optional<JavaScriptProvider> javaScript;
 
    public RunJavaScriptTaskProcessor(Optional<JavaScriptProvider> javaScriptProvider) {
        this.javaScript = javaScriptProvider;
    }

    @Override
    public boolean isResponsibleFor(WsoCommonMessage requestMessage) {
        return "runjs".equals(requestMessage.getCommand());
    }

    @Override
    public void processWsoMessage(WsoCommonMessage request, WsoContext context) {
        if (!javaScript.isPresent()) {
            sendUnavailableResponse(context, request);
            return;
        }
        context.getTaskExecutor().execute(() -> {
            WsoCommonMessage responseMsg = new WsoCommonMessage(request.getReference());
            try {
                WsoCommonMessage outputMsg = new WsoCommonMessage(request.getReference());
                JSCallContext jsCallCtx = new JSCallContext((String output) -> {
                    outputMsg.setBodydata(output);
                    context.sendMessage(outputMsg);
                });

                javaScript.get().run(jsCallCtx,
                        request.getFunctionModule(),
                        Tool.parseArgsFrom(request.getArgsSrc(), request.getAttachments()));

                responseMsg.setSuccess("JavaScript run done");
            } catch (Exception e) {
                String errorText = String.format("ERROR running js command [%s] [%s] [%s]", request.getCommand(),
                        request.getFunctionModule(), Tool.getStackTraceFrom(e));
                LOG.error(errorText);
                responseMsg.setError(errorText);
            } finally {
                context.sendMessage(responseMsg);
            }
        });
    }

    /**
     */
    private void sendUnavailableResponse(WsoContext context, WsoCommonMessage request) {
        WsoCommonMessage responseMsg = new WsoCommonMessage(request.getReference());
        responseMsg.setError("JavaScript provider not installed/available - cannot run JavaScript");
        context.sendMessage(responseMsg);
    }
}
