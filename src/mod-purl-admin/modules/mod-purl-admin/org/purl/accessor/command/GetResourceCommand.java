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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import org.purl.accessor.ResourceFilter;
import org.purl.accessor.util.NKHelper;
import org.purl.accessor.util.ResourceStorage;
import org.purl.accessor.util.SearchHelper;
import org.purl.accessor.util.URIResolver;
import org.ten60.netkernel.layer1.nkf.INKFAsyncRequestHandle;
import org.ten60.netkernel.layer1.nkf.INKFConvenienceHelper;
import org.ten60.netkernel.layer1.nkf.INKFRequest;
import org.ten60.netkernel.layer1.nkf.INKFResponse;
import org.ten60.netkernel.layer1.nkf.NKFException;
import org.ten60.netkernel.layer1.representation.IAspectNVP;
import org.ten60.netkernel.xml.representation.IAspectXDA;
import org.ten60.netkernel.xml.xda.IXDAReadOnly;
import org.ten60.netkernel.xml.xda.IXDAReadOnlyIterator;
import org.ten60.netkernel.xml.xda.XPathLocationException;

import com.ten60.netkernel.urii.IURAspect;
import com.ten60.netkernel.urii.IURRepresentation;
import com.ten60.netkernel.urii.aspect.IAspectString;
import com.ten60.netkernel.urii.aspect.StringAspect;

public class GetResourceCommand extends PURLCommand {

    private SearchHelper search;
    private ResourceFilter filter;

    public GetResourceCommand(String type, URIResolver uriResolver, ResourceStorage resStorage, SearchHelper search) {
        this(type, uriResolver, resStorage, search, null);
    }

    public GetResourceCommand(String type, URIResolver uriResolver, ResourceStorage resStorage, SearchHelper search, ResourceFilter filter) {
        super(type, uriResolver, resStorage);
        this.search = search;
        this.filter = filter;
    }

    @Override
    public INKFResponse execute(INKFConvenienceHelper context) {
        INKFResponse retValue = null;

        try {
            String path = context.getThisRequest().getArgument("path");
            Iterator itor = context.getThisRequest().getArguments();

            while(itor.hasNext()) {
                System.out.println(itor.next());
            }

            if(!path.endsWith("/")) {
                String id = NKHelper.getLastSegment(context);
                if(resStorage.resourceExists(context, uriResolver)) {
                    IURAspect asp = resStorage.getResource(context, uriResolver);

                    // Filter the response if we have a filter
                    if(filter!=null) {
                        asp = filter.filter(context, asp);
                    }

                    // Default response code of 200 is fine
                    IURRepresentation rep = NKHelper.setResponseCode(context, asp, 200);
                    rep = NKHelper.attachGoldenThread(context, "gt:" + path , rep);
                    retValue = context.createResponseFrom(rep);
                    retValue.setCacheable();
                    retValue.setMimeType(NKHelper.MIME_XML);
                } else {
                    IURRepresentation rep = NKHelper.setResponseCode(context, new StringAspect("No such resource: " + id), 404);
                    retValue = context.createResponseFrom(rep);
                    retValue.setMimeType(NKHelper.MIME_TEXT);
                }
            } else {
                IAspectNVP params = (IAspectNVP) context.sourceAspect( "this:param:param", IAspectNVP.class);
                Iterator namesItor = params.getNames().iterator();
                INKFAsyncRequestHandle handles[] = new INKFAsyncRequestHandle[params.getNames().size()];
                IURRepresentation results[] = new IURRepresentation[params.getNames().size()];
                String keys[] = new String[params.getNames().size()];

                int idx = 0;

                while(namesItor.hasNext()) {
                    // TODO: Make this more efficient
                    String key = (String) namesItor.next();

                    if(key.equals("tombstone")) {
                        continue;
                    }
                    
                    String value = params.getValue(key);
                    
                    System.out.println("key: " + key);
                    
                    if(value.length() == 0) {
                        continue;
                    }

                    System.out.println("value: " + value);
                    
                    INKFRequest req = context.createSubRequest("active:purl-search");
                    req.addArgument("index", "ffcpl:/index/" + type);

                    // See if the keyword values need any processing or filtering
                    
                    StringTokenizer st = new StringTokenizer(value, " ,");
                    int kwidx = 0;
                    
                    // We build up arguments this way to encourage caching of the search results
                    
                    while(st.hasMoreTokens()) {
                        String next = search.processKeyword(context, key, st.nextToken());
                        
                        // If we've expanded the list, we'll add in each of the new keywords
                        
                        if(next.contains(" ")) {
                            StringTokenizer st1 = new StringTokenizer(next, " ");
                            while(st1.hasMoreTokens()) {
                                req.addArgument(getKeywordName(kwidx++), "keyword:" + st1.nextToken()); 
                            }
                            
                        } else {
                            req.addArgument(getKeywordName(kwidx++), "keyword:" + next); 
                        }
                    }
                    
                    // TODO: Add basis references if you can get it to constrain properly
                    handles[idx] = context.issueAsyncSubRequest(req);
                    keys[idx] = key;
                    idx++;
                }
                
                for(int i = 0; i < idx; i++ ) {
                    try {
                        results[i] = handles[i].join();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } 
                }
                
                Set<String> alreadyDoneSet = new HashSet<String>();
                StringBuffer sb = new StringBuffer("<results>");
                
                for(int i = 0; i < idx; i++ ) {
                    if(results[i] != null) {
                        // This assumes XML search results, if that isn't always going to be the case
                        // this might break.
                        
                        String uris[] = search.processResults(context, keys[i], results[i]);
                        
                        for(String uri: uris) {
                            if(!alreadyDoneSet.contains(uri)) {
                                if(resStorage.resourceExists(context, uri)) {
                                    IURAspect iur = resStorage.getResource(context, uri);
                                    if(iur != null) {
                                        // Filter the response if we have a filter
                                        if(filter!=null) {
                                            iur = filter.filter(context, iur);
                                        }

                                        StringAspect sa = (StringAspect) context.transrept(iur, IAspectString.class);
                                        sb.append(sa.getString());
                                    }
                                }
                                alreadyDoneSet.add(uri);
                            }                           
                        }
                    }
                }
                
                sb.append("</results>");

                System.out.println(sb.toString());

                retValue = context.createResponseFrom(new StringAspect(sb.toString()));
                retValue.setMimeType(NKHelper.MIME_XML);
            }

        } catch (NKFException e) {
            // TODO Handle
            e.printStackTrace();
        }

        return retValue;
    }
    
    private String getKeywordName(int kwidx) {
        // TODO: Optimize this to avoid object creation for, say up to ten keywords
        // and then return dynamically generated names for pathological cases.
        return "keyword" + kwidx;
    }
}
