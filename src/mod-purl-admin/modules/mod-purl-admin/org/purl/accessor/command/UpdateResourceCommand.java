package org.purl.accessor.command;

/**
 *=========================================================================
 *
 *  Copyright (C) 2007 OCLC (http://oclc.org)
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *=========================================================================
 *
 */

import org.purl.accessor.util.AccessController;
import org.purl.accessor.util.NKHelper;
import org.purl.accessor.util.PURLException;
import org.purl.accessor.util.ResourceCreator;
import org.purl.accessor.util.ResourceStorage;
import org.purl.accessor.util.URIResolver;
import org.ten60.netkernel.layer1.nkf.INKFConvenienceHelper;
import org.ten60.netkernel.layer1.nkf.INKFRequest;
import org.ten60.netkernel.layer1.nkf.INKFResponse;
import org.ten60.netkernel.layer1.nkf.NKFException;
import org.ten60.netkernel.layer1.representation.IAspectNVP;

import com.ten60.netkernel.urii.IURAspect;
import com.ten60.netkernel.urii.IURRepresentation;
import com.ten60.netkernel.urii.aspect.StringAspect;

public class UpdateResourceCommand extends PURLCommand {
    private ResourceCreator resCreator;

    public UpdateResourceCommand(String type, URIResolver uriResolver, AccessController accessController, ResourceCreator resCreator, ResourceStorage resStorage) {
        super(type, uriResolver, accessController, resStorage);
        this.resCreator = resCreator;
    }

    @Override
    public INKFResponse execute(INKFConvenienceHelper context) {
        INKFResponse retValue = null;
        String id = null;

        try {
            //IAspectNVP params = getParams(context);
            id = NKHelper.getLastSegment(context);

            String path = context.getThisRequest().getArgument("path").toLowerCase();

            if(path.startsWith("ffcpl:")) {
                path = path.substring(6);
            }

            if(resStorage.resourceExists(context,uriResolver)) {
                String user = NKHelper.getUser(context);
                
                if(accessController.userHasAccess(context, user, uriResolver.getURI(context))) {
                    try {
                        // Update the user

                        //PUT should come across on the param2 param

                        IAspectNVP params = getParams(context);
                        IURAspect iur = resCreator.createResource(context, params);
                        if(resStorage.updateResource(context, uriResolver, iur)) {
                            recordCommandState(context, "UPDATE", path);

                            // TODO: Should we block on this?
                            //INKFRequest req = context.createSubRequest("active:purl-index");
                            //req.addArgument("path", uriResolver.getURI(context));
                            //req.addArgument("index", "ffcpl:/index/" + type);
                            //req.addArgument("operand", iur);
                            //context.issueAsyncSubRequest(req);

                            //NKHelper.indexResource(context, "ffcpl:/index/" + type, id, res);
                            //NKHelper.indexResource(context, "ffcpl:/index/purls", uriResolver.getURI(context), iur);

                            String message = "Updated resource: " + id;
                            IURRepresentation rep = NKHelper.setResponseCode(context, new StringAspect(message), 200);
                            retValue = context.createResponseFrom(rep);
                            retValue.setMimeType(NKHelper.MIME_TEXT);
                            NKHelper.log(context,message);

                            // Cut golden thread for the resource
                            INKFRequest req = context.createSubRequest("active:cutGoldenThread");
                            req.addArgument("param", "gt:" + path);
                            context.issueSubRequest(req);
                        } else {
                            // TODO: Handle failed update
                            NKHelper.log(context, "ERROR UPDATING RESOURCE");
                        }

                    } catch(PURLException p) {
                        IURRepresentation rep = NKHelper.setResponseCode(context, new StringAspect(p.getMessage()), p.getResponseCode());
                        retValue = context.createResponseFrom(rep);
                        retValue.setMimeType(NKHelper.MIME_TEXT);
                        NKHelper.log(context, p.getMessage());
                    }
                } else {
                    IURRepresentation rep = NKHelper.setResponseCode(context, new StringAspect("Not allowed to update: " + id), 403);
                    retValue = context.createResponseFrom(rep);
                    retValue.setMimeType(NKHelper.MIME_TEXT);
                }
            } else {
                String message = "Cannot update. No such resource: " + id;
                IURRepresentation rep = NKHelper.setResponseCode(context, new StringAspect(message), 404);
                retValue = context.createResponseFrom(rep);
                retValue.setMimeType(NKHelper.MIME_TEXT);
                NKHelper.log(context,message);
            }

        } catch (NKFException e) {
            // TODO Handle
            e.printStackTrace();
        }

        if(id != null) {
            NKHelper.cutGoldenThread(context, "gt:" + type + ":" + id);
        }

        return retValue;
    }

}
