package com.thegrizzlylabs.sardine.impl;

import com.thegrizzlylabs.sardine.DavAce;
import com.thegrizzlylabs.sardine.DavAcl;
import com.thegrizzlylabs.sardine.DavPrincipal;
import com.thegrizzlylabs.sardine.DavQuota;
import com.thegrizzlylabs.sardine.DavResource;
import com.thegrizzlylabs.sardine.Sardine;
import com.thegrizzlylabs.sardine.impl.handler.ExistsResponseHandler;
import com.thegrizzlylabs.sardine.impl.handler.InputStreamResponseHandler;
import com.thegrizzlylabs.sardine.impl.handler.LockResponseHandler;
import com.thegrizzlylabs.sardine.impl.handler.MultiStatusResponseHandler;
import com.thegrizzlylabs.sardine.impl.handler.ResourcesResponseHandler;
import com.thegrizzlylabs.sardine.impl.handler.ResponseHandler;
import com.thegrizzlylabs.sardine.impl.handler.VoidResponseHandler;
import com.thegrizzlylabs.sardine.model.Ace;
import com.thegrizzlylabs.sardine.model.Acl;
import com.thegrizzlylabs.sardine.model.Allprop;
import com.thegrizzlylabs.sardine.model.Exclusive;
import com.thegrizzlylabs.sardine.model.Group;
import com.thegrizzlylabs.sardine.model.Lockinfo;
import com.thegrizzlylabs.sardine.model.Lockscope;
import com.thegrizzlylabs.sardine.model.Locktype;
import com.thegrizzlylabs.sardine.model.Multistatus;
import com.thegrizzlylabs.sardine.model.Owner;
import com.thegrizzlylabs.sardine.model.PrincipalCollectionSet;
import com.thegrizzlylabs.sardine.model.Prop;
import com.thegrizzlylabs.sardine.model.Propertyupdate;
import com.thegrizzlylabs.sardine.model.Propfind;
import com.thegrizzlylabs.sardine.model.Propstat;
import com.thegrizzlylabs.sardine.model.QuotaAvailableBytes;
import com.thegrizzlylabs.sardine.model.QuotaUsedBytes;
import com.thegrizzlylabs.sardine.model.Remove;
import com.thegrizzlylabs.sardine.model.SearchRequest;
import com.thegrizzlylabs.sardine.model.Set;
import com.thegrizzlylabs.sardine.model.Write;
import com.thegrizzlylabs.sardine.report.SardineReport;
import com.thegrizzlylabs.sardine.util.SardineUtil;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by guillaume on 08/11/2017.
 */

public class OkHttpSardine implements Sardine {

    private OkHttpClient client;

    public OkHttpSardine() {
        this.client = new OkHttpClient.Builder().build();
    }

    public OkHttpSardine(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public void setCredentials(String username, String password, boolean isPreemptive) {
        OkHttpClient.Builder builder = client.newBuilder();
        if (isPreemptive) {
            builder.addInterceptor(new AuthenticationInterceptor(username, password));
        } else {
            builder.authenticator(new BasicAuthenticator(username, password));
        }
        this.client = builder.build();
    }

    @Override
    public void setCredentials(String username, String password) {
        setCredentials(username, password, false);
    }

    private class AuthenticationInterceptor implements Interceptor {

        private String userName;
        private String password;

        public AuthenticationInterceptor(@NotNull String userName, @NotNull String password) {
            this.userName = userName;
            this.password = password;
        }

        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request request = chain.request().newBuilder().addHeader("Authorization", Credentials.basic(userName, password)).build();
            return chain.proceed(request);
        }
    }

    @Override
    public List<DavResource> getResources(String url) throws IOException {
        return list(url);
    }

    @Override
    public List<DavResource> list(String url) throws IOException {
        return list(url, 1);
    }

    @Override
    public List<DavResource> list(String url, int depth) throws IOException {
        return list(url, depth, true);
    }

    @Override
    public List<DavResource> list(String url, int depth, java.util.Set<QName> props) throws IOException {
        Propfind body = new Propfind();
        Prop prop = new Prop();
//        prop.setGetcontentlength(objectFactory.createGetcontentlength());
//        prop.setGetlastmodified(objectFactory.createGetlastmodified());
//        prop.setCreationdate(objectFactory.createCreationdate());
//        prop.setDisplayname(objectFactory.createDisplayname());
//        prop.setGetcontenttype(objectFactory.createGetcontenttype());
//        prop.setResourcetype(objectFactory.createResourcetype());
//        prop.setGetetag(objectFactory.createGetetag());
        addCustomProperties(prop, props);
        body.setProp(prop);
        return propfind(url, depth, body);
    }

    @Override
    public List<DavResource> list(String url, int depth, boolean allProp) throws IOException {
        if (allProp) {
            Propfind body = new Propfind();
            body.setAllprop(new Allprop());
            return propfind(url, depth, body);
        } else {
            return list(url, depth, Collections.<QName>emptySet());
        }
    }

    @Override
    public List<DavResource> propfind(String url, int depth, java.util.Set<QName> props) throws IOException {
        Propfind body = new Propfind();
        Prop prop = new Prop();
        addCustomProperties(prop, props);
        body.setProp(prop);
        return propfind(url, depth, body);
    }

    private void addCustomProperties(Prop prop, java.util.Set<QName> props) {
        List<Element> any = prop.getAny();
        for (QName entry : props) {
            Element element = SardineUtil.createElement(entry);
            any.add(element);
        }
    }

    protected List<DavResource> propfind(String url, int depth, Propfind body) throws IOException {
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));
        Request request = new Request.Builder()
                .url(url)
                .header("Depth", depth < 0 ? "infinity" : Integer.toString(depth))
                .method("PROPFIND", requestBody)
                .build();

        return execute(request, new ResourcesResponseHandler());
    }

    @Override
    public <T> T report(String url, int depth, SardineReport<T> report) throws IOException {
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), report.toXml());
        Request request = new Request.Builder()
                .url(url)
                .header("Depth", depth < 0 ? "infinity" : Integer.toString(depth))
                .method("REPORT", requestBody)
                .build();

        Multistatus multistatus = this.execute(request, new MultiStatusResponseHandler());
        return report.fromMultistatus(multistatus);
    }

    @Override
    public List<DavResource> search(String url, String language, String query) throws IOException {
        SearchRequest searchBody = new SearchRequest(language, query);
        String body = SardineUtil.toXml(searchBody);
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));
        Request request = new Request.Builder()
                .url(url)
                .method("SEARCH", requestBody)
                .build();

        return execute(request, new ResourcesResponseHandler());
    }

    @Override
    public void setCustomProps(String url, Map<String, String> set, List<String> remove) throws IOException {
        this.patch(url, SardineUtil.toQName(set), SardineUtil.toQName(remove));
    }

    @Override
    public List<DavResource> patch(String url, Map<QName, String> setProps) throws IOException {
        return this.patch(url, setProps, Collections.<QName>emptyList());
    }

    @Override
    public List<DavResource> patch(String url, Map<QName, String> setProps, List<QName> removeProps) throws IOException {
        List<Element> setPropsElements = new ArrayList<>();
        for (Map.Entry<QName, String> entry : setProps.entrySet()) {
            Element element = SardineUtil.createElement(entry.getKey());
            element.setTextContent(entry.getValue());
            setPropsElements.add(element);
        }
        return this.patch(url, setPropsElements, removeProps);
    }

    @Override
    public List<DavResource> patch(String url, List<Element> setProps, List<QName> removeProps) throws IOException {
        // Build WebDAV <code>PROPPATCH</code> entity.
        Propertyupdate body = new Propertyupdate();
        // Add properties
        {
            Set set = new Set();
            body.getRemoveOrSet().add(set);
            Prop prop = new Prop();
            // Returns a reference to the live list
            List<Element> any = prop.getAny();
            any.addAll(setProps);
            set.setProp(prop);
        }
        // Remove properties
        {
            Remove remove = new Remove();
            body.getRemoveOrSet().add(remove);
            Prop prop = new Prop();
            // Returns a reference to the live list
            List<Element> any = prop.getAny();
            for (QName entry : removeProps) {
                Element element = SardineUtil.createElement(entry);
                any.add(element);
            }
            remove.setProp(prop);
        }

        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));
        Request request = new Request.Builder()
                .url(url)
                .method("PROPPATCH", requestBody)
                .build();

        return execute(request, new ResourcesResponseHandler());
    }

    @Override
    public InputStream get(String url) throws IOException {
        return this.get(url, Collections.<String, String>emptyMap());
    }

    @Override
    public InputStream get(String url, Map<String, String> headers) throws IOException {
        return this.get(url, Headers.of(headers));
    }

    public InputStream get(String url, Headers headers) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .headers(headers)
                .build();

        return execute(request, new InputStreamResponseHandler());
    }

    @Override
    public void put(String url, byte[] data) throws IOException {
        this.put(url, data, null);
    }

    @Override
    public void put(String url, byte[] data, String contentType) throws IOException {
        MediaType mediaType = contentType == null ? null : MediaType.parse(contentType);
        RequestBody requestBody = RequestBody.create(mediaType, data);
        put(url, requestBody);
    }

    @Override
    public void put(String url, File localFile, String contentType) throws IOException {
        //don't use ExpectContinue for repetable FileEntity, some web server (IIS for exmaple) may return 400 bad request after retry
        put(url, localFile, contentType, false);
    }

    @Override
    public void put(String url, File localFile, String contentType, boolean expectContinue) throws IOException {
        MediaType mediaType = contentType == null ? null : MediaType.parse(contentType);
        RequestBody requestBody = RequestBody.create(mediaType, localFile);
        Headers.Builder headersBuilder = new Headers.Builder();
        if (expectContinue) {
            headersBuilder.add("Expect", "100-Continue");
        }
        put(url, requestBody, headersBuilder.build());
    }

    private void put(String url, RequestBody requestBody) throws IOException {
        put(url, requestBody, new Headers.Builder().build());
    }

    private void put(String url, RequestBody requestBody, @NotNull Headers headers) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .put(requestBody)
                .headers(headers)
                .build();
        execute(request);
    }

    @Override
    public void delete(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();
        execute(request);
    }

    @Override
    public void createDirectory(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .method("MKCOL", null)
                .build();
        execute(request);
    }

    @Override
    public void move(String sourceUrl, String destinationUrl) throws IOException {
        move(sourceUrl, destinationUrl, true);
    }

    @Override
    public void move(String sourceUrl, String destinationUrl, boolean overwrite) throws IOException {
        Request request = new Request.Builder()
                .url(sourceUrl)
                .method("MOVE", null)
                .header("DESTINATION", URI.create(destinationUrl).toASCIIString())
                .header("OVERWRITE", overwrite ? "T" : "F")
                .build();
        execute(request);
    }

    @Override
    public void copy(String sourceUrl, String destinationUrl) throws IOException {
        copy(sourceUrl, destinationUrl, true);
    }

    @Override
    public void copy(String sourceUrl, String destinationUrl, boolean overwrite) throws IOException {
        Request request = new Request.Builder()
                .url(sourceUrl)
                .method("COPY", null)
                .header("DESTINATION", URI.create(destinationUrl).toASCIIString())
                .header("OVERWRITE", overwrite ? "T" : "F")
                .build();
        execute(request);
    }

    @Override
    public boolean exists(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Depth", "0")
                .method("PROPFIND", null)
                .build();

        return execute(request, new ExistsResponseHandler());
    }

    @Override
    public String lock(String url) throws IOException {
        Lockinfo body = new Lockinfo();
        Lockscope scopeType = new Lockscope();
        scopeType.setExclusive(new Exclusive());
        body.setLockscope(scopeType);
        Locktype lockType = new Locktype();
        lockType.setWrite(new Write());
        body.setLocktype(lockType);

        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));

        Request request = new Request.Builder()
                .url(url)
                .method("LOCK", requestBody)
                .build();
        return execute(request, new LockResponseHandler());
    }

    @Override
    public String refreshLock(String url, String token, String file) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .method("LOCK", null)
                .header("If", "<" + file + "> (<" + token + ">)")
                .build();
        return execute(request, new LockResponseHandler());
    }

    @Override
    public void unlock(String url, String token) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .method("UNLOCK", null)
                .header("Lock-Token", "<" + token + ">")
                .build();

        execute(request, new VoidResponseHandler());
    }

    @Override
    public DavAcl getAcl(String url) throws IOException {
        Propfind body = new Propfind();
        Prop prop = new Prop();
        prop.setOwner(new Owner());
        prop.setGroup(new Group());
        prop.setAcl(new Acl());
        body.setProp(prop);

        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));
        Request request = new Request.Builder()
                .url(url)
                .header("Depth", "0")
                .method("PROPFIND", requestBody)
                .build();

        Multistatus multistatus = this.execute(request, new MultiStatusResponseHandler());
        List<com.thegrizzlylabs.sardine.model.Response> responses = multistatus.getResponse();
        if (responses.isEmpty()) {
            return null;
        } else {
            return new DavAcl(responses.get(0));
        }
    }

    @Override
    public DavQuota getQuota(String url) throws IOException {
        Propfind body = new Propfind();
        Prop prop = new Prop();
        prop.setQuotaAvailableBytes(new QuotaAvailableBytes());
        prop.setQuotaUsedBytes(new QuotaUsedBytes());
        body.setProp(prop);

        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));
        Request request = new Request.Builder()
                .url(url)
                .header("Depth", "0")
                .method("PROPFIND", requestBody)
                .build();

        Multistatus multistatus = this.execute(request, new MultiStatusResponseHandler());
        List<com.thegrizzlylabs.sardine.model.Response> responses = multistatus.getResponse();
        if (responses.isEmpty()) {
            return null;
        } else {
            return new DavQuota(responses.get(0));
        }
    }

    @Override
    public void setAcl(String url, List<DavAce> aces) throws IOException {
        // Build WebDAV <code>ACL</code> entity.
        Acl body = new Acl();
        body.setAce(new ArrayList<Ace>());
        for (DavAce davAce : aces) {
            // protected and inherited acl must not be part of ACL http request
            if (davAce.getInherited() != null || davAce.isProtected()) {
                continue;
            }
            Ace ace = davAce.toModel();
            body.getAce().add(ace);
        }
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));
        Request request = new Request.Builder()
                .url(url)
                .method("ACL", requestBody)
                .build();

        this.execute(request, new VoidResponseHandler());
    }

    @Override
    public List<DavPrincipal> getPrincipals(String url) throws IOException {
        Propfind body = new Propfind();
        Prop prop = new Prop();
        /*prop.setDisplayname(new Displayname());
        prop.setResourcetype(new Resourcetype());
        prop.setPrincipalURL(new PrincipalURL());*/
        body.setProp(prop);

        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));
        Request request = new Request.Builder()
                .url(url)
                .header("Depth", "1")
                .method("PROPFIND", requestBody)
                .build();

        Multistatus multistatus = this.execute(request, new MultiStatusResponseHandler());
        List<com.thegrizzlylabs.sardine.model.Response> responses = multistatus.getResponse();
        if (responses.isEmpty()) {
            return null;
        } else {
            List<DavPrincipal> collections = new ArrayList<>();
            for (com.thegrizzlylabs.sardine.model.Response r : responses) {
                if (r.getPropstat() != null) {
                    for (Propstat propstat : r.getPropstat()) {
                        if (propstat.getProp() != null
                                && propstat.getProp().getResourcetype() != null
                                && propstat.getProp().getResourcetype().getPrincipal() != null) {
                            collections.add(new DavPrincipal(DavPrincipal.PrincipalType.HREF,
                                    r.getHref()/*.get(0)*/,
                                    propstat.getProp().getDisplayname()/*.getContent().get(0)*/));
                        }
                    }
                }
            }
            return collections;
        }
    }

    @Override
    public List<String> getPrincipalCollectionSet(String url) throws IOException {
        Propfind body = new Propfind();
        Prop prop = new Prop();
        prop.setPrincipalCollectionSet(new PrincipalCollectionSet());
        body.setProp(prop);

        RequestBody requestBody = RequestBody.create(MediaType.parse("text/xml"), SardineUtil.toXml(body));
        Request request = new Request.Builder()
                .url(url)
                .header("Depth", "0")
                .method("PROPFIND", requestBody)
                .build();

        Multistatus multistatus = execute(request, new MultiStatusResponseHandler());
        List<com.thegrizzlylabs.sardine.model.Response> responses = multistatus.getResponse();
        if (responses.isEmpty()) {
            return null;
        } else {
            List<String> collections = new ArrayList<>();
            for (com.thegrizzlylabs.sardine.model.Response r : responses) {
                if (r.getPropstat() != null) {
                    for (Propstat propstat : r.getPropstat()) {
                        if (propstat.getProp() != null
                                && propstat.getProp().getPrincipalCollectionSet() != null
                                && propstat.getProp().getPrincipalCollectionSet().getHref() != null) {
                            collections.add(propstat.getProp().getPrincipalCollectionSet().getHref());
                        }
                    }
                }
            }
            return collections;
        }
    }

    @Override
    public void enableCompression() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void disableCompression() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ignoreCookies() {
        throw new UnsupportedOperationException();
    }
    private void execute(Request request) throws IOException {
        execute(request, new VoidResponseHandler());
    }

    private <T> T execute(Request request, ResponseHandler<T> responseHandler) throws IOException {
        Response response = client.newCall(request).execute();
        return responseHandler.handleResponse(response);
    }

}
