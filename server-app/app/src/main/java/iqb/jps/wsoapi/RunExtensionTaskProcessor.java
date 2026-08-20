/* Authored by iqbserve.de */

package iqb.jps.wsoapi;

import iqb.jps.appcomp.ExtensionHandler;
import iqb.jps.appcomp.ExtensionHandler.ExtensionCallContext;
import iqb.jps.wsoapi.base.WsoCommonMessage;
import iqb.jps.wsoapi.base.WsoContext;
import iqb.jps.wsoapi.base.WsoTaskProcessor;

import iqb.jps.core.HelperTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunExtensionTaskProcessor implements WsoTaskProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RunExtensionTaskProcessor.class);
    private static final HelperTool Tool = HelperTool.getInstance();

    private final ExtensionHandler extensionHandler;

    public RunExtensionTaskProcessor(ExtensionHandler extensionHandler) {
        this.extensionHandler = extensionHandler;
    }

    @Override
    public boolean isResponsibleFor(WsoCommonMessage requestMessage) {
        return "runext".equals(requestMessage.getCommand());
    }

    @Override
    public void processWsoMessage(WsoCommonMessage request, WsoContext context) {
        context.getTaskExecutor().execute(() -> {
            WsoCommonMessage responseMsg = new WsoCommonMessage(request.getReference());

            try {
                WsoCommonMessage outputMsg = new WsoCommonMessage(request.getReference());
                ExtensionCallContext extCallCtx = new ExtensionCallContext((String output) -> {
                    outputMsg.setBodydata(output);
                    context.sendMessage(outputMsg);
                });

                String returnValue = extensionHandler.run(extCallCtx,
                        request.getFunctionModule(),
                        (Object[]) Tool.parseArgsFrom(request.getArgsSrc(), request.getAttachments()));

                responseMsg.setSuccess(returnValue);
            } catch (Exception e) {
                String errorText = String.format("ERROR running extension command [%s] [%s] [%s]",
                        request.getCommand(),
                        request.getFunctionModule(), Tool.getStackTraceFrom(e));
                LOG.error(errorText);
                responseMsg.setError(errorText);
            } finally {
                context.sendMessage(responseMsg);
            }
        });
    }
}
