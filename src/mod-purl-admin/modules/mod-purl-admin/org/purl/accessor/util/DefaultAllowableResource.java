package org.purl.accessor.util;

import org.ten60.netkernel.layer1.nkf.INKFConvenienceHelper;
import org.ten60.netkernel.layer1.nkf.NKFException;

public class DefaultAllowableResource implements AllowableResource {
    private ResourceStorage resStorage;
    private URIResolver resResolver;
    
    public DefaultAllowableResource(ResourceStorage resStorage, URIResolver resResolver) {
        this.resStorage = resStorage;
        this.resResolver = resResolver;
    }

    public boolean allow(INKFConvenienceHelper context, String resourceName) {
        boolean retValue = false;
        try {
            retValue =  !resStorage.resourceExists(context, resResolver);
        } catch(NKFException nfe) {
            nfe.printStackTrace();
        }
        
        return retValue;
    }
    
    public String getDenyMessage(INKFConvenienceHelper context, String resourceName) {
        return "Resource: " + resResolver.getURI(resourceName) + " already exists.";
    }

}
