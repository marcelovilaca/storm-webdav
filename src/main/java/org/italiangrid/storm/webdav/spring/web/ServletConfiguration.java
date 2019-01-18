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
import org.italiangrid.storm.webdav.server.PathResolver;
import org.italiangrid.storm.webdav.server.servlet.ChecksumFilter;
import org.italiangrid.storm.webdav.server.servlet.LogRequestFilter;
import org.italiangrid.storm.webdav.server.servlet.MiltonFilter;
import org.italiangrid.storm.webdav.server.servlet.SAIndexServlet;
import org.italiangrid.storm.webdav.server.servlet.StoRMServlet;
import org.italiangrid.storm.webdav.tpc.LocalURLService;
import org.italiangrid.storm.webdav.tpc.TransferFilter;
import org.italiangrid.storm.webdav.tpc.http.HttpTransferClientMetricsWrapper;
import org.italiangrid.storm.webdav.tpc.transfer.TransferClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.MetricsServlet;



@Configuration
public class ServletConfiguration {

  static final int LOG_REQ_FILTER_ORDER = DEFAULT_FILTER_ORDER + 1000;
  static final int CHECKSUM_FILTER_ORDER = DEFAULT_FILTER_ORDER + 2000;
  static final int TPC_FILTER_ORDER = DEFAULT_FILTER_ORDER + 3000;
  static final int MILTON_FILTER_ORDER = DEFAULT_FILTER_ORDER + 4000;


  @Bean
  FilterRegistrationBean<LogRequestFilter> logRequestFilter() {
    FilterRegistrationBean<LogRequestFilter> logRequestFilter =
        new FilterRegistrationBean<>(new LogRequestFilter());

    logRequestFilter.addUrlPatterns("/*");
    logRequestFilter.setOrder(LOG_REQ_FILTER_ORDER);
    return logRequestFilter;
  }

  @Bean
  @ConditionalOnProperty(name="storm.checksum-filter.enabled", havingValue="true")
  FilterRegistrationBean<ChecksumFilter> checksumFilter(ExtendedAttributesHelper helper,
      PathResolver resolver) {
    FilterRegistrationBean<ChecksumFilter> filter =
        new FilterRegistrationBean<>(new ChecksumFilter(helper, resolver));

    filter.addUrlPatterns("/*");
    filter.setOrder(CHECKSUM_FILTER_ORDER);
    return filter;
  }

  @Bean
  FilterRegistrationBean<MiltonFilter> miltonFilter(FilesystemAccess fsAccess,
      ExtendedAttributesHelper attrsHelper, PathResolver resolver) {
    FilterRegistrationBean<MiltonFilter> miltonFilter =
        new FilterRegistrationBean<>(new MiltonFilter(fsAccess, attrsHelper, resolver));
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
