package com.jivesoftware.boundaries.restz.hc432;

import com.google.gson.Gson;
import com.jivesoftware.boundaries.restz.ConnectionCloser;
import com.jivesoftware.boundaries.restz.ConnectionClosingInputStream;
import com.jivesoftware.boundaries.restz.HttpVerb;
import com.jivesoftware.boundaries.restz.Response;
import com.jivesoftware.boundaries.restz.exceptions.CheckedAsRuntimeException;
import com.jivesoftware.boundaries.restz.Executor;
import com.jivesoftware.boundaries.restz.RequestBuilder;
import com.jivesoftware.boundaries.restz.multipart.*;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.*;
import org.apache.http.entity.*;
import org.apache.http.message.BasicNameValuePair;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by bmoshe on 8/31/14.
 */
public class HC432Executor
implements Executor
{
    private final static Logger log = Logger.getLogger(HC432Executor.class.getSimpleName());

    private final HttpClient httpClient;

    public HC432Executor(HttpClient httpClient)
    {
        this.httpClient = httpClient;
    }

    @Override
    public Response execute(RequestBuilder requestBuilder)
    throws IOException
    {
        final HttpRequestBase request = build(requestBuilder);
        final Response response = execute(request);

        return response;
    }

    public HttpRequestBase build(RequestBuilder requestBuilder)
    {
        final String url = requestBuilder.getUrl();
        final HttpVerb httpVerb = requestBuilder.getHttpVerb();

        HttpRequestBase request = null;
        HttpEntity httpEntity;

        switch(httpVerb)
        {
            case GET:
                request = new HttpGet(url);

                break;

            case PUT:
                HttpPut put = new HttpPut(url);

                httpEntity = acquireEntity(requestBuilder);
                put.setEntity(httpEntity);

                request = put;

                break;

            case POST:
                HttpPost post = new HttpPost(url);

                httpEntity = acquireEntity(requestBuilder);
                post.setEntity(httpEntity);

                request = post;

                break;

            case DELETE:
                request = new HttpDelete(url);

                break;

            case OPTIONS:
                request = new HttpOptions(url);

                break;
        }

        addHeaders(requestBuilder, request);
        return request;
    }

    private HttpEntity acquireEntity(RequestBuilder requestBuilder)
    {
        Object entity = requestBuilder.getEntity();
        HttpEntity httpEntity;

        if(entity == null)
            httpEntity = acquireFormParams(requestBuilder);
        else
        if(entity instanceof MultipartEntityBuilder)
            httpEntity = buildMultipart((MultipartEntityBuilder) entity);

        else
        if(entity instanceof InputStream)
        {
            Long streamLength = requestBuilder.getStreamLength();
            if(streamLength == null)
                httpEntity = new InputStreamEntity((InputStream) entity);

            else
                httpEntity = new InputStreamEntity((InputStream) entity, streamLength);
        }
        else
        if(entity instanceof byte[])
            httpEntity = new ByteArrayEntity((byte[]) entity);

        else
        if(entity instanceof File)
        {
            File entityAsFile = (File) entity;
            String contentType = probeContentTypeByFile(entityAsFile);

            httpEntity = new FileEntity(entityAsFile, ContentType.create(contentType));
        }
        else
            httpEntity = serializeAsRequestEntity(entity);

        return httpEntity;
    }

    private HttpEntity buildMultipart(MultipartEntityBuilder entity)
    {
        final org.apache.http.entity.mime.MultipartEntityBuilder multipartEntityBuilder = org.apache.http.entity.mime.MultipartEntityBuilder.create();

        final List<Part> parts = entity.getParts();
        for(Part part : parts)
            if(part instanceof TextPart)
            {
                final TextPart textPart = (TextPart) part;

                final ContentType contentType = ContentType.create(textPart.getContentType());
                multipartEntityBuilder.addTextBody(textPart.getName(), textPart.getText(), contentType);
            }
            else
            if(part instanceof InputStreamPart)
            {
                final InputStreamPart inPart = (InputStreamPart) part;

                final ContentType contentType = ContentType.create(inPart.getContentType());
                multipartEntityBuilder.addBinaryBody(inPart.getName(), inPart.getContent(), contentType, inPart.getFileName());
            }
            else
            if(part instanceof FilePart)
            {
                final FilePart filePart = (FilePart) part;

                final ContentType contentType = ContentType.create(filePart.getContentType());
                multipartEntityBuilder.addBinaryBody(filePart.getName(), filePart.getContent(), contentType, filePart.getFileName());
            }
            else
            if(part instanceof ByteArrayPart)
            {
                final ByteArrayPart byteArrayPart = (ByteArrayPart) part;

                final ContentType contentType = ContentType.create(byteArrayPart.getContentType());
                multipartEntityBuilder.addBinaryBody(byteArrayPart.getName(), byteArrayPart.getContent(), contentType, byteArrayPart.getFileName());
            }

        final HttpEntity httpEntity = multipartEntityBuilder.build();
        return httpEntity;
    }

    private String probeContentTypeByFile(File file)
    {
        try
        {
            Path entityFilePath = Paths.get(file.toURI());

            String contentType = Files.probeContentType(entityFilePath);
            return contentType;
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return "application/octet-stream";
        }
    }

    private UrlEncodedFormEntity acquireFormParams(RequestBuilder requestBuilder)
    {
        final Set<String> paramNames = requestBuilder.getParamNames();
        if(!paramNames.isEmpty())
        {
            try
            {
                List<NameValuePair> formParams = new LinkedList<>();

                for(String name : paramNames)
                {
                    final String value = requestBuilder.getParam(name);
                    formParams.add(new BasicNameValuePair(name, value));
                }

                UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(formParams);
                return formEntity;
            }
            catch (UnsupportedEncodingException e)
            {
                log.log(Level.WARNING, "Failed to process form params - " + e.getMessage());
                throw new CheckedAsRuntimeException("Failed to process form params - " + e.getMessage(), e);
            }
        }

        return null;
    }

    private void addHeaders(RequestBuilder requestBuilder, HttpUriRequest request)
    {
        final Set<String> headerNames = requestBuilder.getHeaderNames();
        for(String name : headerNames)
        {
            List<String> values = requestBuilder.getHeader(name);
            for(String value : values)
                request.addHeader(name, value);
        }
    }

    private Response execute(HttpRequestBase request)
    throws IOException
    {
        HttpResponse response = httpClient.execute(request);

        javax.ws.rs.core.Response.Status status = extractStatus(response);
        MultivaluedMap<String, String> headers = extractHeaders(response);
        InputStream content = extractContent(response);

        ConnectionCloser connectionCloser = new HC432ConnectionCloser(request);
        content = new ConnectionClosingInputStream(content, connectionCloser);

        return new Response(status, headers, content, connectionCloser);
    }

    private javax.ws.rs.core.Response.Status extractStatus(HttpResponse response)
    {
        // Get Status Code
        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();

        javax.ws.rs.core.Response.Status status = javax.ws.rs.core.Response.Status.fromStatusCode(statusCode);
        return status;
    }

    private MultivaluedMap<String, String> extractHeaders(HttpResponse response)
    {
        // Get Header MultivaluedMap
        Header[] rawHeaders = response.getAllHeaders();
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();

        for(Header header : rawHeaders)
        {
            String name = header.getName();
            String value = header.getValue();

            headers.putSingle(name, value);
        }

        return headers;
    }

    private InputStream extractContent(HttpResponse response)
    {
        try
        {
            HttpEntity entity = response.getEntity();
            if(entity != null)
                return entity.getContent();

            return null;
        }
        catch(IOException e)
        {
            log.log(Level.WARNING, "Failed to extract response content - " + e.getMessage());
            throw new CheckedAsRuntimeException("Failed to extract response content - " + e.getMessage(), e);
        }
    }

    private static HttpEntity serializeAsRequestEntity(Object o)
    {
        Gson gson = new Gson();
        String json = gson.toJson(o);

        return new StringEntity(json, "UTF-8");
    }
}
