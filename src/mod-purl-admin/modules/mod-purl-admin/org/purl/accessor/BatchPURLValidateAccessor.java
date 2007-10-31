package org.purl.accessor;

import org.ten60.netkernel.layer1.nkf.INKFConvenienceHelper;
import org.ten60.netkernel.layer1.nkf.INKFRequest;
import org.ten60.netkernel.layer1.nkf.INKFRequestReadOnly;
import org.ten60.netkernel.layer1.nkf.INKFResponse;
import org.ten60.netkernel.layer1.nkf.impl.NKFAccessorImpl;
import org.ten60.netkernel.xml.representation.IAspectXDA;
import org.ten60.netkernel.xml.xda.IXDAReadOnly;
import org.ten60.netkernel.xml.xda.IXDAReadOnlyIterator;

import com.ten60.netkernel.urii.IURRepresentation;
import com.ten60.netkernel.urii.aspect.IAspectString;
import com.ten60.netkernel.urii.aspect.StringAspect;

public class BatchPURLValidateAccessor extends NKFAccessorImpl {

    public BatchPURLValidateAccessor() {
        super(SAFE_FOR_CONCURRENT_USE, INKFRequestReadOnly.RQT_SOURCE);
    }
    @Override
    public void processRequest(INKFConvenienceHelper context) throws Exception {
//      String path=context.getThisRequest().getArgument("path");
        IAspectXDA xdaParam = (IAspectXDA) context.sourceAspect("this:param:param", IAspectXDA.class);
        System.out.println("**URI: " + context.getThisRequest().getURI());

        // Validate the input document against the batch schema
        INKFRequest req = context.createSubRequest("active:validateRNG");
        req.addArgument("operand", xdaParam);
        req.addArgument("operator", "ffcpl:/schemas/batchPurls.rng");
        req.setAspectClass(IAspectXDA.class);
        IAspectXDA xda = (IAspectXDA)context.issueSubRequestForAspect(req);
        IXDAReadOnly xdaRO = xda.getXDA();
        INKFResponse resp = null;

        // TODO: Make this more declarative
        // TODO: What's the best way to do this?
        if(xdaRO.isTrue("/b/text()='t'")) {
        //if(true) {
            StringBuffer sb = new StringBuffer("<purl-batch-validate>");

            IXDAReadOnlyIterator xdaROItor = xdaParam.getXDA().readOnlyIterator("/purls/purl");
            while(xdaROItor.hasNext()) {
                xdaROItor.next();

                //TODO: Handle transactions

                String pid = xdaROItor.getText("@pid", true);
                req=context.createSubRequest("active:purl-validate");
                req.addArgument("path", "ffcpl:" + pid );
                req.addArgument("method", context.source(context.getThisRequest().getArgument("method")));
                req.addArgument("mode", "mode:validate");
                req.addArgument("requestURL", context.getThisRequest().getArgument("requestURL"));

                try {
                    IURRepresentation iur=context.issueSubRequest(req);
                    IAspectString sa = (IAspectString) context.transrept(iur,IAspectString.class);
                    sb.append(sa.getString());
                } catch(Throwable t) {
                    t.printStackTrace();
                }
            }

            sb.append("</purl-batch-validate>");
            resp = context.createResponseFrom(new StringAspect(sb.toString()));
        } else {
            StringAspect result = (StringAspect) context.transrept(xda, IAspectString.class);
            StringBuffer sb = new StringBuffer("<purl-batch-validate-error>");
            IXDAReadOnlyIterator xdaROItor = xdaRO.readOnlyIterator("/b/errors/error");
            while(xdaROItor.hasNext()) {
                xdaROItor.next();
                sb.append("<error xpath=\"");
                sb.append(xdaROItor.getText("@xpath", true));
                sb.append("\">");
                sb.append(xdaROItor.getText(".", true));
                sb.append("</error>");
            }
            System.out.println(result.getString());
            sb.append("</purl-batch-validate-error>");
            StringAspect sa = new StringAspect(sb.toString());
            resp = context.createResponseFrom(sa);
        }

        resp.setMimeType("text/xml");
        context.setResponse(resp);
    }

}