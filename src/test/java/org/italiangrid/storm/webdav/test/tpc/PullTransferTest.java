/**
 * Copyright (c) Istituto Nazionale di Fisica Nucleare, 2018.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.italiangrid.storm.webdav.test.tpc;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyEnumeration;
import static java.util.Collections.enumeration;
import static org.hamcrest.Matchers.is;
import static org.italiangrid.storm.webdav.server.servlet.WebDAVMethod.COPY;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.servlet.ServletException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.common.collect.Multimap;

@RunWith(MockitoJUnitRunner.class)
public class PullTransferTest extends TransferFilterTestSupport {

  
  @Before
  public void setup() {
    super.setup();
    when(request.getMethod()).thenReturn(COPY.name());
    when(request.getServletPath()).thenReturn(SERVLET_PATH);
    when(request.getPathInfo()).thenReturn(LOCAL_PATH);
    when(request.getHeader(SOURCE_HEADER)).thenReturn(HTTP_URL);
    when(request.getHeaderNames()).thenReturn(emptyEnumeration());
    when(resolver.pathExists(FULL_LOCAL_PATH)).thenReturn(false);
    when(resolver.pathExists(FULL_LOCAL_PATH_PARENT)).thenReturn(true);
  }

  @Test
  public void pullEmptyTransferHeaders() throws IOException, ServletException {
    filter.doFilter(request, response, chain);
    verify(client).handle(getXferRequest.capture(), Mockito.any());
    assertThat(getXferRequest.getValue().path(), is(FULL_LOCAL_PATH));
    assertThat(getXferRequest.getValue().remoteURI(), is(HTTP_URL_URI));
    assertThat(getXferRequest.getValue().overwrite(), is(true));
    assertThat(getXferRequest.getValue().verifyChecksum(), is(true));
    assertTrue("Expected empty xfer headers", getXferRequest.getValue().transferHeaders().isEmpty());
  }

  @Test
  public void overwriteHeaderRecognized() throws IOException, ServletException {
    when(request.getHeader(OVERWRITE_HEADER)).thenReturn("F");
    filter.doFilter(request, response, chain);
    verify(client).handle(getXferRequest.capture(), Mockito.any());
    assertThat(getXferRequest.getValue().path(), is(FULL_LOCAL_PATH));
    assertThat(getXferRequest.getValue().remoteURI(), is(HTTP_URL_URI));
    assertThat("Overwrite header not recognized", getXferRequest.getValue().overwrite(), is(false));
    assertThat(getXferRequest.getValue().verifyChecksum(), is(true));
    assertTrue("Expected empty xfer headers", getXferRequest.getValue().transferHeaders().isEmpty());
  }

  @Test
  public void checksumRecognized() throws IOException, ServletException {
    when(request.getHeader(REQUIRE_CHECKSUM_HEADER)).thenReturn("false");
    filter.doFilter(request, response, chain);
    verify(client).handle(getXferRequest.capture(), Mockito.any());
    assertThat(getXferRequest.getValue().path(), is(FULL_LOCAL_PATH));
    assertThat(getXferRequest.getValue().remoteURI(), is(HTTP_URL_URI));
    assertThat(getXferRequest.getValue().overwrite(), is(true));
    assertThat("RequireChecksumVerification header not recognized",
        getXferRequest.getValue().verifyChecksum(), is(false));
    assertTrue("Expected empty xfer headers", getXferRequest.getValue().transferHeaders().isEmpty());
  }

  @Test
  public void checkTransferHeaderPassing() throws IOException, ServletException {
    when(request.getHeader(TRANSFER_HEADER_AUTHORIZATION_KEY))
      .thenReturn(TRANSFER_HEADER_AUTHORIZATION_VALUE);
    when(request.getHeader(TRANSFER_HEADER_WHATEVER_KEY))
      .thenReturn(TRANSFER_HEADER_WHATEVER_VALUE);

    when(request.getHeaderNames()).thenReturn(
        enumeration(asList(TRANSFER_HEADER_AUTHORIZATION_KEY, TRANSFER_HEADER_WHATEVER_KEY)));

    filter.doFilter(request, response, chain);
    verify(client).handle(getXferRequest.capture(), Mockito.any());

    assertThat(getXferRequest.getValue().path(), is(FULL_LOCAL_PATH));
    assertThat(getXferRequest.getValue().remoteURI(), is(HTTP_URL_URI));
    assertThat(getXferRequest.getValue().overwrite(), is(true));
    assertThat(getXferRequest.getValue().verifyChecksum(), is(true));


    Multimap<String, String> xferHeaders = getXferRequest.getValue().transferHeaders();
    assertThat(xferHeaders.size(), is(2));
    assertThat(xferHeaders.containsKey("Authorization"), is(true));
    assertThat(xferHeaders.get("Authorization").iterator().next(),
        is(TRANSFER_HEADER_AUTHORIZATION_VALUE));
    assertThat(xferHeaders.containsKey("Whatever"), is(true));
    assertThat(xferHeaders.get("Whatever").iterator().next(), is(TRANSFER_HEADER_WHATEVER_VALUE));
  }
  
  @Test
  public void emptyTransferHeaderAreIgnored() throws IOException, ServletException {
    when(request.getHeaderNames()).thenReturn(
        enumeration(asList(TRANSFER_HEADER, TRANSFER_HEADER_WHATEVER_KEY)));
    
    when(request.getHeader(TRANSFER_HEADER_WHATEVER_KEY))
      .thenReturn(TRANSFER_HEADER_WHATEVER_VALUE);
    
    filter.doFilter(request, response, chain);
    verify(client).handle(getXferRequest.capture(), Mockito.any());

    assertThat(getXferRequest.getValue().path(), is(FULL_LOCAL_PATH));
    assertThat(getXferRequest.getValue().remoteURI(), is(HTTP_URL_URI));
    assertThat(getXferRequest.getValue().overwrite(), is(true));
    assertThat(getXferRequest.getValue().verifyChecksum(), is(true));


    Multimap<String, String> xferHeaders = getXferRequest.getValue().transferHeaders();
    assertThat(xferHeaders.size(), is(1));
    
    assertThat(xferHeaders.containsKey("Whatever"), is(true));
    assertThat(xferHeaders.get("Whatever").iterator().next(), is(TRANSFER_HEADER_WHATEVER_VALUE));
  }
  
}
