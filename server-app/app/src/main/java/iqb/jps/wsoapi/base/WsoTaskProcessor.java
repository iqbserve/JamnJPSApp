/* Authored by iqbserve.de */
package iqb.jps.wsoapi.base;

public interface WsoTaskProcessor {

    /**
     */
    public boolean isResponsibleFor(WsoCommonMessage requestMessage);

    /**
     */
    public void processWsoMessage(WsoCommonMessage requestMessage, WsoContext context);
}
