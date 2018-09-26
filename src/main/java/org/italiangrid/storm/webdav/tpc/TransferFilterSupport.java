package org.italiangrid.storm.webdav.tpc;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static javax.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static org.italiangrid.storm.webdav.server.servlet.WebDAVMethod.COPY;
import static org.italiangrid.storm.webdav.tpc.utils.UrlHelper.isRemoteUrl;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.italiangrid.storm.webdav.server.PathResolver;
import org.italiangrid.storm.webdav.tpc.transfer.error.ChecksumVerificationError;
import org.italiangrid.storm.webdav.tpc.transfer.error.TransferError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class TransferFilterSupport implements TransferConstants {

  public static final Logger LOG = LoggerFactory.getLogger(TransferFilterSupport.class);

  protected final PathResolver resolver;
  protected final boolean verifyChecksum;

  protected TransferFilterSupport(PathResolver resolver, boolean verifyChecksum) {
    this.resolver = resolver;
    this.verifyChecksum = verifyChecksum;
  }

  protected String getScopedPathInfo(HttpServletRequest request) {
    return Paths.get(request.getServletPath(), request.getPathInfo()).toString();
  }

  protected boolean hasRemoteSourceOrDestinationHeader(HttpServletRequest request) {

    Optional<String> source = Optional.ofNullable(request.getHeader(SOURCE_HEADER));
    Optional<String> dest = Optional.ofNullable(request.getHeader(DESTINATION_HEADER));

    return (source.isPresent() && isRemoteUrl(source.get()))
        || (dest.isPresent() && isRemoteUrl(dest.get()));

  }

  protected boolean isTpc(HttpServletRequest request) {
    return COPY.toString().equals(request.getMethod())
        && hasRemoteSourceOrDestinationHeader(request);
  }

  protected Multimap<String, String> getTransferHeaders(HttpServletRequest request,
      HttpServletResponse response) {

    Multimap<String, String> xferHeaders = ArrayListMultimap.create();
    Enumeration<String> headerNames = request.getHeaderNames();

    while (headerNames.hasMoreElements()) {
      String headerName = headerNames.nextElement();

      if (headerName.toLowerCase().startsWith(TRANSFER_HEADER_LC)) {
        String xferHeaderName = headerName.substring(TRANFER_HEADER_LENGTH);
        if (xferHeaderName.trim().length() == 0) {
          LOG.warn("Ignoring invalid transfer header {}", headerName);
          continue;
        }
        xferHeaders.put(xferHeaderName.trim(), request.getHeader(headerName));
      }
    }

    return xferHeaders;
  }

  protected boolean verifyChecksumRequested(HttpServletRequest request) {
    Optional<String> verifyChecksum =
        Optional.ofNullable(request.getHeader(REQUIRE_CHECKSUM_HEADER));

    if (verifyChecksum.isPresent()) {
      return "true".equals(verifyChecksum.get());
    }

    // FIXME: take default from configuration
    return true;
  }

  protected boolean overwriteRequested(HttpServletRequest request) {
    Optional<String> overwrite = Optional.ofNullable(request.getHeader(OVERWRITE_HEADER));

    if (overwrite.isPresent()) {
      return "T".equalsIgnoreCase(overwrite.get());
    }

    return true;
  }


  protected boolean isSupportedTransferURI(URI uri) {
    return SUPPORTED_PROTOCOLS.contains(uri.getScheme()) && !isNull(uri.getPath());
  }

  protected boolean validTransferURI(String xferUri) {

    boolean result = false;

    try {
      URI uri = new URI(xferUri);

      if (!isSupportedTransferURI(uri)) {
        LOG.warn("Unsupported transfer URI: {}", uri);
        result = false;
      } else {
        result = true;
      }



    } catch (URISyntaxException e) {
      LOG.warn("Error parsing transfer URI: {}", e.getMessage());
      result = false;
    }

    return result;

  }

  protected void conflict(HttpServletResponse response, String msg) throws IOException {
    response.sendError(HttpStatus.CONFLICT.value(), msg);
  }

  protected void preconditionFailed(HttpServletResponse response, String msg) throws IOException {
    response.sendError(HttpStatus.PRECONDITION_FAILED.value(), msg);
  }

  protected void notFound(HttpServletResponse response, String msg) throws IOException {
    response.sendError(HttpStatus.NOT_FOUND.value(), msg);
  }

  protected void invalidRequest(HttpServletResponse response, String msg) throws IOException {
    response.sendError(BAD_REQUEST.value(), msg);
  }

  protected boolean validLocalPath(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    String servletPath = request.getServletPath();

    Optional<String> pathInfo = Optional.ofNullable(request.getPathInfo());

    if (!pathInfo.isPresent() || pathInfo.get().trim().length() == 0) {
      invalidRequest(response, "Null or empty local path information!");
      return false;
    }

    if (!pathInfo.get().startsWith("/")) {
      invalidRequest(response, "Invalid local path: " + pathInfo.get());
      return false;
    }

    Path localPath = Paths.get(servletPath, pathInfo.get());
    final boolean overwriteRequested = overwriteRequested(request);

    if (!overwriteRequested && resolver.pathExists(localPath.toString())) {
      preconditionFailed(response, "Target file exists and Overwrite is false");
      return false;
    }

    String parentPath = localPath.getParent().toString();
    if (!resolver.pathExists(parentPath)) {
      conflict(response, "Parent resource does not exist");
      return false;
    }

    return true;
  }


  protected boolean validRequest(HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    Optional<String> source = Optional.ofNullable(request.getHeader(SOURCE_HEADER));
    Optional<String> dest = Optional.ofNullable(request.getHeader(DESTINATION_HEADER));
    Optional<String> overwrite = Optional.ofNullable(request.getHeader(OVERWRITE_HEADER));
    Optional<String> checksum = Optional.ofNullable(request.getHeader(REQUIRE_CHECKSUM_HEADER));

    if (!validLocalPath(request, response)) {
      return false;
    }

    if (source.isPresent() && dest.isPresent()) {
      invalidRequest(response, "Source and Destination headers are both present!");
      return false;
    }

    if (source.isPresent() && !validTransferURI(request.getHeader(SOURCE_HEADER))) {
      invalidRequest(response,
          format("Invalid %s header: %s", SOURCE_HEADER, request.getHeader(SOURCE_HEADER)));
      return false;
    }

    if (dest.isPresent() && !validTransferURI(request.getHeader(DESTINATION_HEADER))) {
      invalidRequest(response, format("Invalid %s header: %s", DESTINATION_HEADER,
          request.getHeader(DESTINATION_HEADER)));
      return false;
    }

    if (overwrite.isPresent()) {
      boolean invalidOverwrite = false;

      String val = overwrite.get();

      if (val.trim().length() == 0) {
        invalidOverwrite = true;
      } else if (val.trim().length() > 1) {
        invalidOverwrite = true;
      } else if (!"T".equalsIgnoreCase(val) && !"F".equalsIgnoreCase(val)) {
        invalidOverwrite = true;
      }

      if (invalidOverwrite) {
        invalidRequest(response, format("Invalid %s header value: %s", OVERWRITE_HEADER, val));
        return false;
      }
    }

    if (checksum.isPresent()) {

      boolean invalidChecksum = false;

      String val = checksum.get();
      if (val.trim().length() == 0) {
        invalidChecksum = true;
      } else if (!"true".equals(val) && !"false".equals(val)) {
        invalidChecksum = true;
      }

      if (invalidChecksum) {
        invalidRequest(response,
            format("Invalid %s header value: %s", REQUIRE_CHECKSUM_HEADER, val));
        return false;
      }
    }

    return true;
  }

  public void handleChecksumVerificationError(ChecksumVerificationError e,
      HttpServletResponse response) throws IOException {
    response.sendError(SC_PRECONDITION_FAILED, e.getMessage());
  }

  public void handleTransferError(TransferError e, HttpServletResponse response)
      throws IOException {
    response.sendError(SC_PRECONDITION_FAILED, e.getMessage());
  }

  public void handleClientProtocolException(ClientProtocolException e, HttpServletResponse response)
      throws IOException {
    response.sendError(SC_PRECONDITION_FAILED,
        format("Third party transfer error: %s", e.getMessage()));
  }

  public void handleHttpResponseException(HttpResponseException e, HttpServletResponse response)
      throws IOException {
    response.sendError(SC_PRECONDITION_FAILED,
        format("Third party transfer error: %d %s", e.getStatusCode(), e.getMessage()));
  }

}
