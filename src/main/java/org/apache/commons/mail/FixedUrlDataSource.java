package org.apache.commons.mail;

import javax.activation.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * @author jingxs
 */

public class FixedUrlDataSource implements DataSource {
    private URL url = null;
    private URLConnection urlConnection = null;

    public FixedUrlDataSource(URL url){
        this.url = url;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        //fixed 复用了urlConnection
        URLConnection connection = getUrlConnection();
        return connection == null ? null : connection.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        URLConnection connection = getUrlConnection();
        return connection == null ? null : connection.getOutputStream();
    }

    @Override
    public String getContentType() {
        URLConnection connection = null;
        try {
            connection = getUrlConnection();
        } catch (IOException ignored) {
            // ignore
        }
        return connection == null ? "application/octet-stream" : connection.getContentType();
    }

    @Override
    public String getName() {
        return url.getFile();
    }

    private URLConnection getUrlConnection() throws IOException {
        if (urlConnection == null) {
            urlConnection = url.openConnection();
        }
        return urlConnection;
    }
}
