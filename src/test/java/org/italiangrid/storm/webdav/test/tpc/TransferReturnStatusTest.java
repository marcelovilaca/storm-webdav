package org.italiangrid.storm.webdav.test.tpc;

import static java.util.Collections.emptyEnumeration;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;
import static org.hamcrest.CoreMatchers.is;
import static org.italiangrid.storm.webdav.server.servlet.WebDAVMethod.COPY;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.italiangrid.storm.webdav.tpc.transfer.GetTransferRequest;
import org.italiangrid.storm.webdav.tpc.transfer.error.ChecksumVerificationError;
import org.italiangrid.storm.webdav.tpc.transfer.error.TransferError;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TransferReturnStatusTest extends TransferFilterTestSupport {

  @Before
  public void setup() {
    super.setup();
    when(request.getMethod()).thenReturn(COPY.name());
    when(request.getServletPath()).thenReturn(SERVLET_PATH);
    when(request.getPathInfo()).thenReturn(LOCAL_PATH);
    when(request.getHeader(SOURCE_HEADER)).thenReturn(HTTP_URL);
    when(request.getHeaderNames()).thenReturn(emptyEnumeration());
    when(resolver.pathExists(FULL_LOCAL_PATH_PARENT)).thenReturn(true);
  }

  @Test
  public void filterAnswers201() throws IOException, ServletException {
    filter.doFilter(request, response, chain);
    verify(response).setStatus(httpStatus.capture());
    assertThat(httpStatus.getValue(), is(SC_CREATED));
  }

  @Test
  public void filterAnswers412ForClientProtocolException() throws IOException, ServletException {
    Mockito.doThrow(new ClientProtocolException("Connection error"))
      .when(client)
      .handle(ArgumentMatchers.<GetTransferRequest>any(), ArgumentMatchers.any());
    
    filter.doFilter(request, response, chain);
    verify(response).sendError(httpStatus.capture(), error.capture());
    assertThat(httpStatus.getValue(), is(SC_PRECONDITION_FAILED));
    assertThat(error.getValue(), is("Third party transfer error: Connection error"));
  }
  
  @Test
  public void filterAnswers412ForHttpExceptionError() throws IOException, ServletException {
    Mockito.doThrow(new HttpResponseException(HttpServletResponse.SC_FORBIDDEN, "Access denied"))
      .when(client)
      .handle(ArgumentMatchers.<GetTransferRequest>any(), ArgumentMatchers.any());
    
    filter.doFilter(request, response, chain);
    verify(response).sendError(httpStatus.capture(), error.capture());
    assertThat(httpStatus.getValue(), is(SC_PRECONDITION_FAILED));
    assertThat(error.getValue(), is("Third party transfer error: 403 Access denied"));
  }
  
  @Test
  public void filterAnswers412ForChecksumVerificationError() throws IOException, ServletException {
    Mockito.doThrow(new ChecksumVerificationError("Checksum verification error"))
      .when(client)
      .handle(ArgumentMatchers.<GetTransferRequest>any(), ArgumentMatchers.any());
    
    filter.doFilter(request, response, chain);
    verify(response).sendError(httpStatus.capture(), error.capture());
    assertThat(httpStatus.getValue(), is(SC_PRECONDITION_FAILED));
    assertThat(error.getValue(), is("Checksum verification error"));
  }
  
  @Test
  public void filterAnswers412ForGenericTransferError() throws IOException, ServletException {
    Mockito.doThrow(new TransferError("Error"))
      .when(client)
      .handle(ArgumentMatchers.<GetTransferRequest>any(), ArgumentMatchers.any());
    
    filter.doFilter(request, response, chain);
    verify(response).sendError(httpStatus.capture(), error.capture());
    assertThat(httpStatus.getValue(), is(SC_PRECONDITION_FAILED));
    assertThat(error.getValue(), is("Error"));
  }

}
