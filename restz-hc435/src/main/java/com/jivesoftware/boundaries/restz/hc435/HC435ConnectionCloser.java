package com.jivesoftware.boundaries.restz.hc435;

import com.jivesoftware.boundaries.restz.ConnectionCloser;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;

/**
 * Created by bmoshe on 4/10/14.
 */
public class HC435ConnectionCloser
implements ConnectionCloser
{
    private final HttpRequestBase request;

    public HC435ConnectionCloser(HttpRequestBase request)
    {
        this.request = request;
    }

    @Override
    public void close()
    throws IOException
    {
        request.releaseConnection();
    }
}
