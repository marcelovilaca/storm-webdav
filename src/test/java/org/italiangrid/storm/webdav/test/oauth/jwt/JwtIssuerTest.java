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
package org.italiangrid.storm.webdav.test.oauth.jwt;

import static java.util.Collections.emptySet;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.italiangrid.storm.webdav.oauth.authzserver.jwt.DefaultJwtTokenIssuer.CLAIM_AUTHORITIES;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.RandomStringUtils;
import org.italiangrid.storm.webdav.authz.AuthorizationPolicyService;
import org.italiangrid.storm.webdav.authz.SAPermission;
import org.italiangrid.storm.webdav.authz.VOMSAuthenticationDetails;
import org.italiangrid.storm.webdav.config.ServiceConfigurationProperties.AuthorizationServerProperties;
import org.italiangrid.storm.webdav.oauth.authzserver.AccessTokenRequest;
import org.italiangrid.storm.webdav.oauth.authzserver.jwt.DefaultJwtTokenIssuer;
import org.italiangrid.voms.VOMSAttribute;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;

@RunWith(MockitoJUnitRunner.class)
public class JwtIssuerTest {

  public static final String ISSUER = "https://storm.example";
  public static final String AUTHN_SUBJECT = "CN=test";
  public static final String SECRET = RandomStringUtils.random(256, true, true);

  public static final int MAX_TOKEN_LIFETIME_SEC = 43200;

  public static final Instant NOW = Instant.parse("2018-01-01T00:00:00.00Z");

  public static final Instant EXPIRATION_INSTANT =
      NOW.plusSeconds(MAX_TOKEN_LIFETIME_SEC).truncatedTo(ChronoUnit.SECONDS);

  public static final Instant VOMS_EXPIRATION_INSTANT_EARLY =
      NOW.plusSeconds(100).truncatedTo(ChronoUnit.SECONDS);
  
  public static final Instant VOMS_EXPIRATION_INSTANT_LATE =
      EXPIRATION_INSTANT.plusSeconds(100).truncatedTo(ChronoUnit.SECONDS);
  
  public static final Instant REQUESTED_EXPIRATION_INSTANT_EARLY = 
      NOW.plusSeconds(50).truncatedTo(ChronoUnit.SECONDS);
  
  public static final Instant REQUESTED_EXPIRATION_INSTANT_LATE = 
      EXPIRATION_INSTANT.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);

  Clock fixedClock = Clock.fixed(NOW, ZoneId.systemDefault());

  @Mock
  AuthorizationServerProperties props;

  @Mock
  AuthorizationPolicyService ps;

  @Mock
  Authentication authn;

  @Mock
  AccessTokenRequest req;

  @Mock
  VOMSAuthenticationDetails details;

  @Mock
  VOMSAttribute vomsAttribute;

  DefaultJwtTokenIssuer issuer;

  @Before
  public void setup() {

    when(vomsAttribute.getNotAfter()).thenReturn(Date.from(VOMS_EXPIRATION_INSTANT_LATE));
    when(details.getVomsAttributes()).thenReturn(Lists.newArrayList(vomsAttribute));
    when(props.getIssuer()).thenReturn(ISSUER);
    when(props.getSecret()).thenReturn(SECRET);
    when(props.getMaxTokenLifetimeSec()).thenReturn(MAX_TOKEN_LIFETIME_SEC);
    when(authn.getName()).thenReturn(AUTHN_SUBJECT);
    when(authn.getDetails()).thenReturn(details);
    

    when(ps.getSAPermissions(authn)).thenReturn(emptySet());

    issuer = new DefaultJwtTokenIssuer(fixedClock, props, ps);
  }

  @Test
  public void canCreateSignedJWT() throws ParseException, JOSEException {
    SignedJWT jwt = issuer.createAccessToken(req, authn);

    assertThat(jwt, notNullValue());
    assertThat(jwt.getJWTClaimsSet().getIssuer(), is(ISSUER));
    assertThat(jwt.getJWTClaimsSet().getAudience().get(0), is(ISSUER));
    assertThat(jwt.getJWTClaimsSet().getSubject(), is(AUTHN_SUBJECT));
    assertThat(jwt.getJWTClaimsSet().getClaims(), hasKey(CLAIM_AUTHORITIES));
    assertThat(jwt.getJWTClaimsSet().getStringListClaim(CLAIM_AUTHORITIES), empty());
    assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant(), is(EXPIRATION_INSTANT));

    JWSVerifier verifier = new MACVerifier(SECRET);
    assertThat(jwt.verify(verifier), is(true));
  }


  @Test
  public void returnsAuthoritiesAsExpected() throws ParseException {

    SAPermission canReadTest = SAPermission.canRead("test");
    SAPermission canWriteTest = SAPermission.canWrite("test");

    when(ps.getSAPermissions(authn)).thenReturn(Sets.newHashSet(canReadTest, canWriteTest));

    SignedJWT jwt = issuer.createAccessToken(req, authn);


    assertThat(jwt, notNullValue());
    assertThat(jwt.getJWTClaimsSet().getIssuer(), is(ISSUER));
    assertThat(jwt.getJWTClaimsSet().getSubject(), is(AUTHN_SUBJECT));
    assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant(), is(EXPIRATION_INSTANT));
    assertThat(jwt.getJWTClaimsSet().getClaims(), hasKey(CLAIM_AUTHORITIES));
    assertThat(jwt.getJWTClaimsSet().getStringListClaim(CLAIM_AUTHORITIES), not(empty()));
    assertThat(jwt.getJWTClaimsSet().getStringListClaim(CLAIM_AUTHORITIES),
        hasItems(canReadTest.toString(), canWriteTest.toString()));

  }

  @Test
  public void tokenIssuerLimitsTokenValidityToAcLifetime() throws ParseException {
    SAPermission canReadTest = SAPermission.canRead("test");
    SAPermission canWriteTest = SAPermission.canWrite("test");

    when(ps.getSAPermissions(authn)).thenReturn(Sets.newHashSet(canReadTest, canWriteTest));
    when(vomsAttribute.getNotAfter()).thenReturn(Date.from(VOMS_EXPIRATION_INSTANT_EARLY));
    
    SignedJWT jwt = issuer.createAccessToken(req, authn);
    
    assertThat(jwt, notNullValue());
    assertThat(jwt.getJWTClaimsSet().getIssuer(), is(ISSUER));
    assertThat(jwt.getJWTClaimsSet().getSubject(), is(AUTHN_SUBJECT));
    assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant(), is(VOMS_EXPIRATION_INSTANT_EARLY)); 
  }
  
  @Test
  public void tokenIssuerLimitsTokenValidtyWithRequestedLifetime() throws ParseException {
    SAPermission canReadTest = SAPermission.canRead("test");
    SAPermission canWriteTest = SAPermission.canWrite("test");
    when(ps.getSAPermissions(authn)).thenReturn(Sets.newHashSet(canReadTest, canWriteTest));
    when(vomsAttribute.getNotAfter()).thenReturn(Date.from(VOMS_EXPIRATION_INSTANT_EARLY));
    when(req.getLifetime()).thenReturn(50L);
    
    SignedJWT jwt = issuer.createAccessToken(req, authn);
    assertThat(jwt, notNullValue());
    assertThat(jwt.getJWTClaimsSet().getIssuer(), is(ISSUER));
    assertThat(jwt.getJWTClaimsSet().getSubject(), is(AUTHN_SUBJECT));
    assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant(), is(REQUESTED_EXPIRATION_INSTANT_EARLY));  
  }


  @Test
  public void tokenIssuerIgnoresRequestedLifetimeWhenExceedsInternalLimit() throws ParseException {
    SAPermission canReadTest = SAPermission.canRead("test");
    SAPermission canWriteTest = SAPermission.canWrite("test");
    when(ps.getSAPermissions(authn)).thenReturn(Sets.newHashSet(canReadTest, canWriteTest));
    when(vomsAttribute.getNotAfter()).thenReturn(Date.from(VOMS_EXPIRATION_INSTANT_EARLY));
    when(req.getLifetime()).thenReturn(TimeUnit.DAYS.toSeconds(10));
    
    SignedJWT jwt = issuer.createAccessToken(req, authn);
    assertThat(jwt, notNullValue());
    assertThat(jwt.getJWTClaimsSet().getIssuer(), is(ISSUER));
    assertThat(jwt.getJWTClaimsSet().getSubject(), is(AUTHN_SUBJECT));
    assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant(), is(VOMS_EXPIRATION_INSTANT_EARLY));  
  }
}
