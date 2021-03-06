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
package org.italiangrid.storm.webdav.spring.web;

import static org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER;

import org.italiangrid.storm.webdav.config.StorageAreaConfiguration;
import org.italiangrid.storm.webdav.config.ThirdPartyCopyProperties;
import org.italiangrid.storm.webdav.fs.FilesystemAccess;
import org.italiangrid.storm.webdav.fs.attrs.ExtendedAttributesHelper;
import org.italiangrid.storm.webdav.macaroon.MacaroonIssuerService;
import org.italiangrid.storm.webdav.macaroon.MacaroonRequestFilter;
import org.italiangrid.storm.webdav.milton.util.ReplaceContentStrategy;
import org.italiangrid.storm.webdav.server.PathResolver;
import org.italiangrid.storm.webdav.server.servlet.ChecksumFilter;
import org.italiangrid.storm.webdav.server.servlet.LogRequestFilter;
import org.italiangrid.storm.webdav.server.servlet.MiltonFilter;
import org.italiangrid.storm.webdav.server.servlet.SAIndexServlet;
import org.italiangrid.storm.webdav.server.servlet.StoRMServlet;
import org.italiangrid.storm.webdav.server.tracing.RequestIdFilter;
import org.italiangrid.storm.webdav.tpc.LocalURLService;
import org.italiangrid.storm.webdav.tpc.TransferFilter;
import org.italiangrid.storm.webdav.tpc.http.HttpTransferClientMetricsWrapper;
import org.italiangrid.storm.webdav.tpc.transfer.TransferClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.MetricsServlet;
import com.fasterxml.jackson.databind.ObjectMapper;



@Configuration
public class ServletConfiguration {
  
  public static final Logger LOG = LoggerFactory.getLogger(ServletConfiguration.class);

  static final int REQUEST_ID_FILTER_ORDER = DEFAULT_FILTER_ORDER + 1000;
  static final int LOG_REQ_FILTER_ORDER = DEFAULT_FILTER_ORDER + 1001;
  static final int CHECKSUM_FILTER_ORDER = DEFAULT_FILTER_ORDER + 1002;
  static final int MACAROON_REQ_FILTER_ORDER = DEFAULT_FILTER_ORDER + 1003;
  static final int TPC_FILTER_ORDER = DEFAULT_FILTER_ORDER + 1004;
  static final int MILTON_FILTER_ORDER = DEFAULT_FILTER_ORDER + 1005;


  @Bean
  FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
    FilterRegistrationBean<RequestIdFilter> requestIdFilter =
        new FilterRegistrationBean<>(new RequestIdFilter());

    requestIdFilter.addUrlPatterns("/*");
    requestIdFilter.setOrder(REQUEST_ID_FILTER_ORDER);

    return requestIdFilter;
  }


  @Bean
  FilterRegistrationBean<LogRequestFilter> logRequestFilter() {
    FilterRegistrationBean<LogRequestFilter> logRequestFilter =
        new FilterRegistrationBean<>(new LogRequestFilter());

    logRequestFilter.addUrlPatterns("/*");
    logRequestFilter.setOrder(LOG_REQ_FILTER_ORDER);
    return logRequestFilter;
  }

  @Bean
  @ConditionalOnProperty(name = "storm.checksum-filter.enabled", havingValue = "true")
  FilterRegistrationBean<ChecksumFilter> checksumFilter(ExtendedAttributesHelper helper,
      PathResolver resolver) {
    LOG.info("Checksum filter enabled");
    FilterRegistrationBean<ChecksumFilter> filter =
        new FilterRegistrationBean<>(new ChecksumFilter(helper, resolver));

    filter.addUrlPatterns("/*");
    filter.setOrder(CHECKSUM_FILTER_ORDER);
    return filter;
  }

  @Bean
  @ConditionalOnExpression("${storm.macaroon-filter.enabled} && ${storm.authz-server.enabled}")
  FilterRegistrationBean<MacaroonRequestFilter> macaroonRequestFilter(ObjectMapper mapper,
      MacaroonIssuerService service) {
    LOG.info("Macaroon request filter enabled");
    FilterRegistrationBean<MacaroonRequestFilter> filter =
        new FilterRegistrationBean<>(
            new MacaroonRequestFilter(mapper, service));
    filter.setOrder(MACAROON_REQ_FILTER_ORDER);
    return filter;
  }

  @Bean
  FilterRegistrationBean<MiltonFilter> miltonFilter(FilesystemAccess fsAccess,
      ExtendedAttributesHelper attrsHelper, PathResolver resolver, ReplaceContentStrategy rcs) {
    FilterRegistrationBean<MiltonFilter> miltonFilter =
        new FilterRegistrationBean<>(new MiltonFilter(fsAccess, attrsHelper, resolver, rcs));
    miltonFilter.addUrlPatterns("/*");
    miltonFilter.setOrder(MILTON_FILTER_ORDER);
    return miltonFilter;
  }


  @Bean
  FilterRegistrationBean<TransferFilter> tpcFilter(FilesystemAccess fs,
      ExtendedAttributesHelper attrsHelper, PathResolver resolver, TransferClient client,
      ThirdPartyCopyProperties props, LocalURLService lus, MetricRegistry registry) {

    TransferClient metricsClient = new HttpTransferClientMetricsWrapper(registry, client);

    FilterRegistrationBean<TransferFilter> tpcFilter = new FilterRegistrationBean<>(
        new TransferFilter(metricsClient, resolver, lus, props.isVerifyChecksum()));
    tpcFilter.addUrlPatterns("/*");
    tpcFilter.setOrder(TPC_FILTER_ORDER);
    return tpcFilter;
  }

  @Bean
  ServletRegistrationBean<MetricsServlet> metricsServlet(MetricRegistry registry) {
    ServletRegistrationBean<MetricsServlet> metricsServlet =
        new ServletRegistrationBean<>(new MetricsServlet(registry), "/status/metrics");
    metricsServlet.setAsyncSupported(false);
    return metricsServlet;
  }

  @Bean
  ServletRegistrationBean<StoRMServlet> stormServlet(StorageAreaConfiguration saConfig,
      PathResolver pathResolver) {

    ServletRegistrationBean<StoRMServlet> stormServlet =
        new ServletRegistrationBean<>(new StoRMServlet(pathResolver));

    stormServlet.addInitParameter("acceptRanges", "true");
    stormServlet.addInitParameter("dirAllowed", "true");
    stormServlet.addInitParameter("aliases", "false");
    stormServlet.addInitParameter("gzip", "false");

    saConfig.getStorageAreaInfo()
      .forEach(i -> i.accessPoints().forEach(m -> stormServlet.addUrlMappings(m + "/*", m)));

    return stormServlet;
  }

  @Bean
  ServletRegistrationBean<SAIndexServlet> saIndexServlet(StorageAreaConfiguration config,
      TemplateEngine engine) {
    return new ServletRegistrationBean<>(new SAIndexServlet(config, engine), "");
  }

}
