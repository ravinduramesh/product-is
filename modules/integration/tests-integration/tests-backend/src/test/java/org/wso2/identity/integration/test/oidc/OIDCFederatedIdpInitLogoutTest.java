/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.identity.integration.test.oidc;

import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.opensaml.xml.util.Base64;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils;
import org.wso2.carbon.identity.application.common.model.idp.xsd.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.idp.xsd.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.idp.xsd.IdentityProviderProperty;
import org.wso2.carbon.identity.application.common.model.idp.xsd.JustInTimeProvisioningConfig;
import org.wso2.carbon.identity.application.common.model.idp.xsd.Property;
import org.wso2.carbon.identity.application.common.model.xsd.AuthenticationStep;
import org.wso2.carbon.identity.application.common.model.xsd.Claim;
import org.wso2.carbon.identity.application.common.model.xsd.ClaimConfig;
import org.wso2.carbon.identity.application.common.model.xsd.ClaimMapping;
import org.wso2.carbon.identity.application.common.model.xsd.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.xsd.OutboundProvisioningConfig;
import org.wso2.carbon.identity.application.common.model.xsd.ServiceProvider;
import org.wso2.carbon.identity.oauth.stub.dto.OAuthConsumerAppDTO;
import org.wso2.carbon.identity.sso.saml.stub.types.SAMLSSOServiceProviderDTO;
import org.wso2.carbon.integration.common.admin.client.AuthenticatorClient;
import org.wso2.carbon.user.mgt.stub.UserAdminUserAdminException;
import org.wso2.identity.integration.common.clients.UserManagementClient;
import org.wso2.identity.integration.common.clients.oauth.OauthAdminClient;
import org.wso2.identity.integration.test.application.mgt.AbstractIdentityFederationTestCase;
import org.wso2.identity.integration.test.oidc.bean.OIDCApplication;
import org.wso2.identity.integration.test.util.Utils;
import org.wso2.identity.integration.test.utils.CommonConstants;
import org.wso2.identity.integration.test.utils.DataExtractUtil;
import org.wso2.identity.integration.test.utils.IdentityConstants;
import org.wso2.identity.integration.test.utils.OAuth2Constant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration test cases for SAML-OIDC federation scenarios.
 */
public class OIDCFederatedIdpInitLogoutTest extends AbstractIdentityFederationTestCase {

    private static final String SAML_SSO_LOGIN_URL = "http://localhost:8490/%s/samlsso?SAML2.HTTPBinding=%s";
    private static final String FEDERATED_SAML_SSO_URL =
            "http://localhost:8490/travelocity.com-saml-tenantwithoutsigning/samlsso?SAML2.HTTPBinding=HTTP-Redirect";
    private static final String PRIMARY_SAML_SSO_URL =
            "http://localhost:8490/travelocity.com/samlsso?SAML2.HTTPBinding=HTTP-Redirect";

    private static final String USER_AGENT = "Apache-HttpClient/4.2.5 (java 1.5)";
    private static final String ACS_URL = "http://localhost:8490/%s/home.jsp";

    private static final String PRIMARY_IS_SP_NAME = "travelocity.com";
    private static final String PRIMARY_IS_SP_INBOUND_AUTH_TYPE_SAMLSSO = "samlsso";
    private static final String PRIMARY_IS_SP_AUTHENTICATION_TYPE = "federated";

    private static final String PRIMARY_IS_SAML_ISSUER_NAME = "travelocity.com";
    private static final String PRIMARY_IS_SAML_ACS_URL =
            "http://localhost:8490/travelocity.com/home.jsp";
    private static final String PRIMARY_IS_SAML_NAME_ID_FORMAT =
            "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress";

    private static final String FEDERATED_IS_SP_NAME = "travelocity.com-saml-tenantwithoutsigning";
    private static final String FEDERATED_IS_SP_INBOUND_AUTH_TYPE_SAMLSSO = "samlsso";

    private static final String FEDERATED_IS_SAML_ISSUER_NAME = "travelocity.com-saml-tenantwithoutsigning";
    private static final String FEDERATED_IS_SAML_ACS_URL =
            "http://localhost:8490/travelocity.com-saml-tenantwithoutsigning/home.jsp";

    private static final String PRIMARY_IS_IDP_NAME = "trustedIdP";
    private static final String PRIMARY_IS_IDP_AUTHENTICATOR_NAME_OIDC = "OpenIDConnectAuthenticator";
    private static final String PRIMARY_IS_IDP_CALLBACK_URL = "https://localhost:9853/commonauth";
    private static final String PRIMARY_IS_BACK_CHANNEL_LOGOUT_ENDPOINT = "https://localhost:9853/identity/oidc/slo";
    private static final String PRIMARY_IS_SESSION_EXTENSION_ENDPOINT = "https://localhost:9853/identity/extend" +
            "-session?idpSessionKey=";

    private static final String SECONDARY_IS_TEST_USERNAME = "testFederatedUser";
    private static final String SECONDARY_IS_TEST_PASSWORD = "testFederatePassword";
    private static final String SECONDARY_IS_TEST_USER_ROLES = "admin";

    private static final String SECONDARY_IS_SP_NAME = "secondarySP";
    private static final String SECONDARY_IS_SAML_SSO_URL = "https://localhost:9854/samlsso";
    private static final String SECONDARY_IS_COMMONAUTH_URL = "https://localhost:9854/commonauth";
    private static final String SECONDARY_IS_TOKEN_ENDPOINT = "https://localhost:9854/oauth2/token";
    private static final String SECONDARY_IS_LOGOUT_ENDPOINT = "https://localhost:9854/oidc/logout";
    private static final String SECONDARY_IS_AUTHORIZE_ENDPOINT = "https://localhost:9854/oauth2/authorize";
    private static final String SECONDARY_IS_JWKS_URI = "https://localhost:9854/oauth2/jwks";
    private static final String SECONDARY_IS_SESSION_EXTENSION_ENDPOINT = "https://localhost:9854/identity/extend" +
            "-session?idpSessionKey=";

    private static final String HTTPS_LOCALHOST_SERVICES = "https://localhost:%s/services/";
    private static final String SAML_IDP_SLO_URL = SECONDARY_IS_SAML_SSO_URL + "?slo=true";

    private static final String TENANT_DOMAIN_PARAM = "tenantDomain";

    protected OauthAdminClient adminClient;
    private String secondaryISClientID;
    private String secondaryISClientSecret;
    private AuthenticatorClient logManger;
    private final String username;
    private final String userPassword;
    private final AutomationContext context;
    private String backendURL;
    private String sessionCookie;
    private Cookie primaryCommonAuthCookie;
    private Cookie federatedCommonAuthCookie;

    private static final int PORT_OFFSET_0 = 0;
    private static final int PORT_OFFSET_1 = 1;

    CookieStore cookieStore;
    private CloseableHttpClient client;

    @DataProvider(name = "configProvider")
    public static Object[][] configProvider() {

        return new Object[][]{{TestUserMode.SUPER_TENANT_ADMIN}};
    }

    @Factory(dataProvider = "configProvider")
    public OIDCFederatedIdpInitLogoutTest(TestUserMode userMode) throws Exception {

        context = new AutomationContext("IDENTITY", userMode);
        this.username = context.getContextTenant().getTenantAdmin().getUserName();
        this.userPassword = context.getContextTenant().getTenantAdmin().getPassword();
    }

    @BeforeClass(alwaysRun = true)
    public void initTest() throws Exception {

        super.initTest();
        backendURL = context.getContextUrls().getBackEndUrl();
        logManger = new AuthenticatorClient(backendURL);
        sessionCookie = logManger.login(username, userPassword, context.getInstance().getHosts().get("default"));

        adminClient = new OauthAdminClient(backendURL, sessionCookie);

        super.createServiceClients(PORT_OFFSET_0, sessionCookie,
                new IdentityConstants.ServiceClientType[]{
                        IdentityConstants.ServiceClientType.APPLICATION_MANAGEMENT,
                        IdentityConstants.ServiceClientType.IDENTITY_PROVIDER_MGT,
                        IdentityConstants.ServiceClientType.SAML_SSO_CONFIG});

        super.createServiceClients(PORT_OFFSET_1, null,
                new IdentityConstants.ServiceClientType[]{
                        IdentityConstants.ServiceClientType.APPLICATION_MANAGEMENT,
                        IdentityConstants.ServiceClientType.OAUTH_ADMIN,
                        IdentityConstants.ServiceClientType.SAML_SSO_CONFIG});

        createServiceProviderForIdpInSecondaryIS();
        createServiceProviderInSecondaryIS();
        createIdentityProviderInPrimaryIS();
        createServiceProviderInPrimaryIS();

        cookieStore = new BasicCookieStore();
        client = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();

        boolean userCreated = addUserToSecondaryIS();
        Assert.assertTrue(userCreated, "User creation failed in secondary IS.");
    }

    @AfterClass(alwaysRun = true)
    public void endTest() throws Exception {

        try {
            super.deleteServiceProvider(PORT_OFFSET_0, PRIMARY_IS_SP_NAME);
            super.deleteServiceProvider(PORT_OFFSET_1, FEDERATED_IS_SP_NAME);
            super.deleteIdentityProvider(PORT_OFFSET_0, PRIMARY_IS_IDP_NAME);
            super.deleteServiceProvider(PORT_OFFSET_1, SECONDARY_IS_SP_NAME);
            deleteAddedUsersInSecondaryIS();

            client.close();
        } catch (Exception e) {
            log.error("Failure occured due to :" + e.getMessage(), e);
            throw e;
        }
    }

    @Test(groups = "wso2.is", description = "Check SAML-to-OIDC federated login")
    public void testPrimarySPLogin() throws Exception {

        String sessionDataKeyOfSecondaryISLogin = sendSAMLRequestToPrimaryIS();
        Assert.assertNotNull(sessionDataKeyOfSecondaryISLogin,
                "Unable to acquire 'sessionDataKey' value in secondary IS");

        String sessionDataKeyConsentOfSecondaryIS = doAuthenticationInSecondaryIS(sessionDataKeyOfSecondaryISLogin);
        Assert.assertNotNull(sessionDataKeyConsentOfSecondaryIS, "Invalid sessionDataKeyConsent.");

        String callbackURLOfPrimaryIS = doConsentApprovalInSecondaryIS(sessionDataKeyConsentOfSecondaryIS);
        Assert.assertNotNull(callbackURLOfPrimaryIS, "Unable to acquire authorizeCallbackURL in primary IS");

        String samlResponse = getSAMLResponseFromPrimaryIS(callbackURLOfPrimaryIS);
        Assert.assertNotNull(samlResponse, "Unable to acquire SAML response from primary IS");

        String decodedSAMLResponse = new String(Base64.decode(samlResponse));
        Assert.assertTrue(decodedSAMLResponse.contains("AuthnContextClassRef"),
                "AuthnContextClassRef is not received.");

        String homepageContent = sendSAMLResponseToWebApp(samlResponse);

        boolean isValidLogin = validateLoginHomePageContent(homepageContent);
        Assert.assertTrue(isValidLogin, "Invalid SAML login response received by travelocity app");

        List<Cookie> cookieList = cookieStore.getCookies();
        for (Cookie cookie : cookieList) {
            if ("commonAuthId".equals(cookie.getName())) {
                primaryCommonAuthCookie = cookie;
            }
        }
        Assert.assertNotNull(primaryCommonAuthCookie, "Couldn't retrieve commonAuthId cookie from cookie store.");
    }

    @Test(groups = "wso2.is", description = "Check SAML federated sp login", dependsOnMethods = {
            "testPrimarySPLogin"})
    public void testFederatedSPLogin() throws Exception {

        HttpResponse response =
                Utils.sendGetRequest(String.format(SAML_SSO_LOGIN_URL, FEDERATED_IS_SP_NAME, "HTTP-POST"), USER_AGENT,
                        client);
        String samlRequest = Utils.extractDataFromResponse(response, CommonConstants.SAML_REQUEST_PARAM, 5);
        response = sendSAMLMessage(SECONDARY_IS_SAML_SSO_URL, CommonConstants.SAML_REQUEST_PARAM, samlRequest);
        EntityUtils.consume(response.getEntity());

        response = Utils.sendRedirectRequest(response, USER_AGENT, ACS_URL, FEDERATED_IS_SP_NAME,
                client);

        String sessionKey = Utils.extractDataFromResponse(response, CommonConstants.SESSION_DATA_KEY, 1);
        response = sendPOSTMessage(sessionKey, SECONDARY_IS_SAML_SSO_URL, USER_AGENT, ACS_URL, FEDERATED_IS_SP_NAME,
                SECONDARY_IS_TEST_USERNAME, SECONDARY_IS_TEST_PASSWORD, client);

        if (Utils.requestMissingClaims(response)) {
            String pastrCookie = Utils.getPastreCookie(response);
            Assert.assertNotNull(pastrCookie, "pastr cookie not found in response.");
            EntityUtils.consume(response.getEntity());

            response = Utils.sendPOSTConsentMessage(response, SECONDARY_IS_COMMONAUTH_URL, USER_AGENT,
                    String.format(ACS_URL, FEDERATED_IS_SP_NAME), client, pastrCookie);
            EntityUtils.consume(response.getEntity());
        }

        String redirectUrl = Utils.getRedirectUrl(response);
        if (StringUtils.isNotBlank(redirectUrl)) {
            response = Utils.sendRedirectRequest(response, USER_AGENT, ACS_URL, FEDERATED_IS_SP_NAME, client);
        }

        String samlResponse = Utils.extractDataFromResponse(response, CommonConstants.SAML_RESPONSE_PARAM, 5);
        EntityUtils.consume(response.getEntity());

        response = sendSAMLMessage(String.format(ACS_URL, FEDERATED_IS_SP_NAME), CommonConstants.SAML_RESPONSE_PARAM,
                samlResponse);
        String resultPage = DataExtractUtil.getContentData(response);

        Assert.assertTrue(resultPage.contains("You are logged in as "),
                "SAML SSO Login failed for " + FEDERATED_IS_SP_NAME);

        List<Cookie> cookieList = cookieStore.getCookies();
        for (Cookie cookie : cookieList) {
            if ("commonAuthId".equals(cookie.getName())) {
                federatedCommonAuthCookie = cookie;
            }
        }
        Assert.assertNotNull(federatedCommonAuthCookie, "Couldn't retrieve commonAuthId cookie from cookie store.");

    }

    @Test(groups = "wso2.is", description = "Check SAML-to-OIDC federated logout", dependsOnMethods = {
            "testFederatedSPLogin"})
    public void testLogout() throws Exception {

        Utils.sendGetRequest(SAML_IDP_SLO_URL, USER_AGENT, client);

        javax.servlet.http.Cookie fedCookie = new javax.servlet.http.Cookie(federatedCommonAuthCookie.getName(),
                federatedCommonAuthCookie.getValue());
        HttpResponse response = Utils.sendGetRequest(
                SECONDARY_IS_SESSION_EXTENSION_ENDPOINT + FrameworkUtils.getHashOfCookie(fedCookie), USER_AGENT,
                client);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_BAD_REQUEST);

        javax.servlet.http.Cookie primCookie = new javax.servlet.http.Cookie(federatedCommonAuthCookie.getName(),
                primaryCommonAuthCookie.getValue());
        Thread.sleep(5 * 1000);
        response = Utils.sendGetRequest(
                PRIMARY_IS_SESSION_EXTENSION_ENDPOINT + FrameworkUtils.getHashOfCookie(primCookie), USER_AGENT, client);
        Assert.assertEquals(response.getStatusLine().getStatusCode(), HttpStatus.SC_BAD_REQUEST);
    }

    public HttpResponse sendSAMLMessage(String url, String samlMsgKey, String samlMsgValue)
            throws IOException {

        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        HttpPost post = new HttpPost(url);
        post.setHeader("User-Agent", USER_AGENT);
        urlParameters.add(new BasicNameValuePair(samlMsgKey, samlMsgValue));
        urlParameters.add(new BasicNameValuePair(TENANT_DOMAIN_PARAM, "carbon.super"));
        post.setEntity(new UrlEncodedFormEntity(urlParameters));

        return client.execute(post);
    }

    public static HttpResponse sendPOSTMessage(String sessionKey, String url, String userAgent, String
            acsUrl, String artifact, String userName, String password, HttpClient httpClient) throws Exception {

        HttpPost post = new HttpPost(url);
        post.setHeader("User-Agent", userAgent);
        post.addHeader("Referer", String.format(acsUrl, artifact));
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("username", userName));
        urlParameters.add(new BasicNameValuePair("password", password));
        urlParameters.add(new BasicNameValuePair("tocommonauth", "true"));
        urlParameters.add(new BasicNameValuePair("sessionDataKey", sessionKey));
        post.setEntity(new UrlEncodedFormEntity(urlParameters));
        return httpClient.execute(post);
    }

    private boolean addUserToSecondaryIS() throws Exception {

        UserManagementClient usrMgtClient = new UserManagementClient(getSecondaryISURI(), "admin", "admin");
        if (usrMgtClient == null) {
            return false;
        } else {
            String[] roles = {SECONDARY_IS_TEST_USER_ROLES};
            usrMgtClient.addUser(SECONDARY_IS_TEST_USERNAME, SECONDARY_IS_TEST_PASSWORD, roles, null);
            if (usrMgtClient.userNameExists(SECONDARY_IS_TEST_USER_ROLES, SECONDARY_IS_TEST_USERNAME)) {
                return true;
            } else {
                return false;
            }
        }
    }

    private void deleteAddedUsersInSecondaryIS() throws RemoteException, UserAdminUserAdminException {

        UserManagementClient usrMgtClient = new UserManagementClient(getSecondaryISURI(), "admin", "admin");
        usrMgtClient.deleteUser(SECONDARY_IS_TEST_USERNAME);
    }

    protected String getSecondaryISURI() {

        return String.format(HTTPS_LOCALHOST_SERVICES, DEFAULT_PORT + PORT_OFFSET_1);
    }

    private void createServiceProviderInPrimaryIS() throws Exception {

        super.addServiceProvider(PORT_OFFSET_0, PRIMARY_IS_SP_NAME);

        ServiceProvider serviceProvider = getServiceProvider(PORT_OFFSET_0, PRIMARY_IS_SP_NAME);
        Assert.assertNotNull(serviceProvider, "Failed to create service provider 'travelocity' in primary IS");

        updateServiceProviderWithSAMLConfigs(PORT_OFFSET_0, PRIMARY_IS_SAML_ISSUER_NAME, PRIMARY_IS_SAML_ACS_URL,
                serviceProvider);

        AuthenticationStep authStep = new AuthenticationStep();
        org.wso2.carbon.identity.application.common.model.xsd.IdentityProvider idP = new org.wso2.carbon.identity.
                application.common.model.xsd.IdentityProvider();
        idP.setIdentityProviderName(PRIMARY_IS_IDP_NAME);
        org.wso2.carbon.identity.application.common.model.xsd.FederatedAuthenticatorConfig oidcAuthnConfig = new
                org.wso2.carbon.identity.application.common.model.xsd.FederatedAuthenticatorConfig();
        oidcAuthnConfig.setName(PRIMARY_IS_IDP_AUTHENTICATOR_NAME_OIDC);
        oidcAuthnConfig.setDisplayName("openidconnect");
        idP.setFederatedAuthenticatorConfigs(new org.wso2.carbon.identity.application.common.model.xsd.
                FederatedAuthenticatorConfig[]{oidcAuthnConfig});

        authStep.setFederatedIdentityProviders(new org.wso2.carbon.identity.application.common.model.xsd.
                IdentityProvider[]{idP});

        serviceProvider.getLocalAndOutBoundAuthenticationConfig().setAuthenticationSteps(new AuthenticationStep[]{
                authStep});
        serviceProvider.getLocalAndOutBoundAuthenticationConfig()
                .setAuthenticationType(PRIMARY_IS_SP_AUTHENTICATION_TYPE);

        updateServiceProvider(PORT_OFFSET_0, serviceProvider);

        serviceProvider = getServiceProvider(PORT_OFFSET_0, PRIMARY_IS_SP_NAME);

        InboundAuthenticationRequestConfig[] configs = serviceProvider.getInboundAuthenticationConfig().
                getInboundAuthenticationRequestConfigs();
        boolean success = false;
        if (configs != null) {
            for (InboundAuthenticationRequestConfig config : configs) {
                if (PRIMARY_IS_SP_INBOUND_AUTH_TYPE_SAMLSSO.equals(config.getInboundAuthType())) {
                    success = true;
                    break;
                }
            }
        }

        Assert.assertTrue(success, "Failed to update service provider with inbound SAML2 configs in primary IS");
        Assert.assertTrue(
                PRIMARY_IS_SP_AUTHENTICATION_TYPE.equals(serviceProvider.getLocalAndOutBoundAuthenticationConfig().
                        getAuthenticationType()), "Failed to update local and out bound configs in primary IS");
    }

    private void createServiceProviderInSecondaryIS() throws Exception {

        super.addServiceProvider(PORT_OFFSET_1, FEDERATED_IS_SP_NAME);

        ServiceProvider serviceProvider = getServiceProvider(PORT_OFFSET_1, FEDERATED_IS_SP_NAME);
        Assert.assertNotNull(serviceProvider, "Failed to create service provider 'travelocity' in primary IS");

        updateServiceProviderWithSAMLConfigs(PORT_OFFSET_1, FEDERATED_IS_SAML_ISSUER_NAME, FEDERATED_IS_SAML_ACS_URL,
                serviceProvider);

        updateServiceProvider(PORT_OFFSET_1, serviceProvider);

        serviceProvider = getServiceProvider(PORT_OFFSET_1, FEDERATED_IS_SP_NAME);

        InboundAuthenticationRequestConfig[] configs = serviceProvider.getInboundAuthenticationConfig().
                getInboundAuthenticationRequestConfigs();
        boolean success = false;
        if (configs != null) {
            for (InboundAuthenticationRequestConfig config : configs) {
                if (FEDERATED_IS_SP_INBOUND_AUTH_TYPE_SAMLSSO.equals(config.getInboundAuthType())) {
                    success = true;
                    break;
                }
            }
        }

        Assert.assertTrue(success, "Failed to update service provider with inbound SAML2 configs in primary IS");
    }

    private void createServiceProviderForIdpInSecondaryIS() throws Exception {

        super.addServiceProvider(PORT_OFFSET_1, SECONDARY_IS_SP_NAME);

        ServiceProvider serviceProvider = getServiceProvider(PORT_OFFSET_1, SECONDARY_IS_SP_NAME);
        Assert.assertNotNull(serviceProvider, "Failed to create service provider 'secondarySP' in secondary IS");

        updateServiceProviderWithOIDCConfigs(PORT_OFFSET_1, SECONDARY_IS_SP_NAME, PRIMARY_IS_IDP_CALLBACK_URL,
                PRIMARY_IS_BACK_CHANNEL_LOGOUT_ENDPOINT, serviceProvider);

        super.updateServiceProvider(PORT_OFFSET_1, serviceProvider);

        serviceProvider = getServiceProvider(PORT_OFFSET_1, SECONDARY_IS_SP_NAME);

        InboundAuthenticationRequestConfig[] configs = serviceProvider.getInboundAuthenticationConfig().
                getInboundAuthenticationRequestConfigs();
        boolean success = false;
        if (configs != null) {
            for (InboundAuthenticationRequestConfig config : configs) {
                if (secondaryISClientID.equals(config.getInboundAuthKey()) && OAuth2Constant.OAUTH_2.equals(
                        config.getInboundAuthType())) {
                    success = true;
                    break;
                }
            }
        }

        Assert.assertTrue(success, "Failed to update service provider with inbound OIDC configs in secondary IS");
    }

    private void createIdentityProviderInPrimaryIS() throws Exception {

        IdentityProvider identityProvider = new IdentityProvider();
        identityProvider.setIdentityProviderName(PRIMARY_IS_IDP_NAME);

        // Set JWKS Uri to identity provider.
        IdentityProviderProperty jwksUriProperty = new IdentityProviderProperty();
        jwksUriProperty.setName("jwksUri");
        jwksUriProperty.setValue(SECONDARY_IS_JWKS_URI);
        IdentityProviderProperty issuerProperty = new IdentityProviderProperty();
        issuerProperty.setName("idpIssuerName");
        issuerProperty.setValue(SECONDARY_IS_TOKEN_ENDPOINT);
        IdentityProviderProperty[] properties = {jwksUriProperty, issuerProperty};
        identityProvider.setIdpProperties(properties);

        FederatedAuthenticatorConfig oidcAuthnConfig = new FederatedAuthenticatorConfig();
        oidcAuthnConfig.setName(PRIMARY_IS_IDP_AUTHENTICATOR_NAME_OIDC);
        oidcAuthnConfig.setDisplayName("openidconnect");
        oidcAuthnConfig.setEnabled(true);
        oidcAuthnConfig.setProperties(getOIDCAuthnConfigProperties());
        identityProvider.setDefaultAuthenticatorConfig(oidcAuthnConfig);
        identityProvider.setFederatedAuthenticatorConfigs(new FederatedAuthenticatorConfig[]{oidcAuthnConfig});

        JustInTimeProvisioningConfig jitConfig = new JustInTimeProvisioningConfig();
        jitConfig.setProvisioningEnabled(true);
        jitConfig.setProvisioningUserStore("PRIMARY");
        identityProvider.setJustInTimeProvisioningConfig(jitConfig);

        super.addIdentityProvider(PORT_OFFSET_0, identityProvider);

        Assert.assertNotNull(getIdentityProvider(PORT_OFFSET_0, PRIMARY_IS_IDP_NAME), "Failed to create " +
                "Identity Provider 'trustedIdP' in primary IS");
    }

    private void updateServiceProviderWithOIDCConfigs(int portOffset, String applicationName, String callbackUrl,
                                                      String backChannelLogoutUrl,
                                                      ServiceProvider serviceProvider) throws Exception {

        OIDCApplication application = new OIDCApplication(applicationName, OAuth2Constant.TRAVELOCITY_APP_CONTEXT_ROOT,
                callbackUrl);

        OAuthConsumerAppDTO appDTO = getOAuthConsumerAppDTO(application);
        appDTO.setBackChannelLogoutUrl(backChannelLogoutUrl);

        OAuthConsumerAppDTO[] appDtos = createOIDCConfiguration(portOffset, appDTO);

        for (OAuthConsumerAppDTO appDto : appDtos) {
            if (appDto.getApplicationName().equals(application.getApplicationName())) {
                application.setClientId(appDto.getOauthConsumerKey());
                application.setClientSecret(appDto.getOauthConsumerSecret());
            }
        }

        ClaimConfig claimConfig = null;
        if (!application.getRequiredClaims().isEmpty()) {
            claimConfig = new ClaimConfig();
            for (String claimUri : application.getRequiredClaims()) {
                Claim claim = new Claim();
                claim.setClaimUri(claimUri);
                ClaimMapping claimMapping = new ClaimMapping();
                claimMapping.setRequested(true);
                claimMapping.setLocalClaim(claim);
                claimMapping.setRemoteClaim(claim);
                claimConfig.addClaimMappings(claimMapping);
            }
        }

        serviceProvider.setClaimConfig(claimConfig);
        serviceProvider.setOutboundProvisioningConfig(new OutboundProvisioningConfig());
        List<InboundAuthenticationRequestConfig> authRequestList = new ArrayList<>();

        if (application.getClientId() != null) {
            InboundAuthenticationRequestConfig inboundAuthenticationRequestConfig = new
                    InboundAuthenticationRequestConfig();
            inboundAuthenticationRequestConfig.setInboundAuthKey(application.getClientId());
            secondaryISClientID = application.getClientId();
            inboundAuthenticationRequestConfig.setInboundAuthType(OAuth2Constant.OAUTH_2);
            if (StringUtils.isNotBlank(application.getClientSecret())) {
                org.wso2.carbon.identity.application.common.model.xsd.Property property = new org.wso2.carbon.identity.
                        application.common.model.xsd.Property();
                property.setName(OAuth2Constant.OAUTH_CONSUMER_SECRET);
                property.setValue(application.getClientSecret());
                secondaryISClientSecret = application.getClientSecret();
                org.wso2.carbon.identity.application.common.model.xsd.Property[] properties = {property};
                inboundAuthenticationRequestConfig.setProperties(properties);
            }
            serviceProvider.getInboundAuthenticationConfig().setInboundAuthenticationRequestConfigs(new
                    InboundAuthenticationRequestConfig[]{inboundAuthenticationRequestConfig});
            authRequestList.add(inboundAuthenticationRequestConfig);
        }

        super.updateServiceProvider(PORT_OFFSET_1, serviceProvider);
    }

    private OAuthConsumerAppDTO getOAuthConsumerAppDTO(OIDCApplication application) {

        OAuthConsumerAppDTO appDTO = new OAuthConsumerAppDTO();
        appDTO.setApplicationName(application.getApplicationName());
        appDTO.setCallbackUrl(application.getCallBackURL());
        appDTO.setOAuthVersion(OAuth2Constant.OAUTH_VERSION_2);
        appDTO.setGrantTypes("authorization_code implicit password client_credentials refresh_token " +
                "urn:ietf:params:oauth:grant-type:saml2-bearer iwa:ntlm");

        return appDTO;
    }

    private Property[] getOIDCAuthnConfigProperties() {

        Property[] properties = new Property[8];
        Property property = new Property();
        property.setName(IdentityConstants.Authenticator.OIDC.IDP_NAME);
        property.setValue("oidcFedIdP");
        properties[0] = property;

        property = new Property();
        property.setName(IdentityConstants.Authenticator.OIDC.CLIENT_ID);
        property.setValue(secondaryISClientID);
        properties[1] = property;

        property = new Property();
        property.setName(IdentityConstants.Authenticator.OIDC.CLIENT_SECRET);
        property.setValue(secondaryISClientSecret);
        properties[2] = property;

        property = new Property();
        property.setName(IdentityConstants.Authenticator.OIDC.OAUTH2_AUTHZ_URL);
        property.setValue(SECONDARY_IS_AUTHORIZE_ENDPOINT);
        properties[3] = property;

        property = new Property();
        property.setName(IdentityConstants.Authenticator.OIDC.OAUTH2_TOKEN_URL);
        property.setValue(SECONDARY_IS_TOKEN_ENDPOINT);
        properties[4] = property;

        property = new Property();
        property.setName(IdentityConstants.Authenticator.OIDC.CALLBACK_URL);
        property.setValue(PRIMARY_IS_IDP_CALLBACK_URL);
        properties[5] = property;

        property = new Property();
        property.setName(IdentityConstants.Authenticator.OIDC.OIDC_LOGOUT_URL);
        property.setValue(SECONDARY_IS_LOGOUT_ENDPOINT);
        properties[6] = property;

        property = new Property();
        property.setName("commonAuthQueryParams");
        property.setValue("scope=" + OAuth2Constant.OAUTH2_SCOPE_OPENID_WITH_INTERNAL_LOGIN);
        properties[7] = property;
        return properties;
    }

    private void updateServiceProviderWithSAMLConfigs(int portOffset, String issuerName, String acsUrl,
                                                      ServiceProvider serviceProvider) throws Exception {

        String attributeConsumingServiceIndex = super.createSAML2WebSSOConfiguration(portOffset,
                getSAMLSSOServiceProviderDTO(issuerName, acsUrl));
        Assert.assertNotNull(attributeConsumingServiceIndex, "Failed to create SAML2 Web SSO configuration for" +
                " issuer '" + issuerName + "'");

        InboundAuthenticationRequestConfig samlAuthenticationRequestConfig = new InboundAuthenticationRequestConfig();
        samlAuthenticationRequestConfig.setInboundAuthKey(issuerName);
        samlAuthenticationRequestConfig.setInboundAuthType(FEDERATED_IS_SP_INBOUND_AUTH_TYPE_SAMLSSO);
        org.wso2.carbon.identity.application.common.model.xsd.Property property = new org.wso2.carbon.identity.
                application.common.model.xsd.Property();
        property.setName("attrConsumServiceIndex");
        property.setValue(attributeConsumingServiceIndex);
        samlAuthenticationRequestConfig.setProperties(new org.wso2.carbon.identity.application.common.model.xsd.
                Property[]{property});

        serviceProvider.getInboundAuthenticationConfig().setInboundAuthenticationRequestConfigs(new
                InboundAuthenticationRequestConfig[]{samlAuthenticationRequestConfig});
    }

    private SAMLSSOServiceProviderDTO getSAMLSSOServiceProviderDTO(String issuerName, String acsUrl) {

        SAMLSSOServiceProviderDTO samlssoServiceProviderDTO = new SAMLSSOServiceProviderDTO();
        samlssoServiceProviderDTO.setIssuer(issuerName);
        samlssoServiceProviderDTO.setAssertionConsumerUrls(new String[]{acsUrl});
        samlssoServiceProviderDTO.setDefaultAssertionConsumerUrl(acsUrl);
        samlssoServiceProviderDTO.setNameIDFormat(PRIMARY_IS_SAML_NAME_ID_FORMAT);
        samlssoServiceProviderDTO.setDoSignAssertions(true);
        samlssoServiceProviderDTO.setDoSignResponse(true);
        samlssoServiceProviderDTO.setDoSingleLogout(true);
        samlssoServiceProviderDTO.setIdPInitSLOEnabled(true);
        samlssoServiceProviderDTO.setEnableAttributeProfile(true);
        samlssoServiceProviderDTO.setEnableAttributesByDefault(true);

        return samlssoServiceProviderDTO;
    }

    private String sendSAMLRequestToPrimaryIS() throws Exception {

        HttpGet request = new HttpGet(PRIMARY_SAML_SSO_URL);
        request.setHeader("User-Agent", USER_AGENT);
        HttpResponse response = client.execute(request);
        return extractValueFromResponse(response, "name=\"sessionDataKey\"", 1);
    }

    private String sendSAMLRequestToFederatedIS() throws Exception {

        HttpGet request = new HttpGet(FEDERATED_SAML_SSO_URL);
        request.setHeader("User-Agent", USER_AGENT);
        HttpResponse response = client.execute(request);
        return extractValueFromResponse(response, "name=\"sessionDataKey\"", 1);
    }

    private String doAuthenticationInSecondaryIS(String sessionDataKey) throws Exception {

        HttpResponse response = sendLoginPost(client, sessionDataKey);
        Assert.assertNotNull(response, "Login request failed. response is null.");

        Header locationHeader = response.getFirstHeader(OAuth2Constant.HTTP_RESPONSE_HEADER_LOCATION);
        Assert.assertNotNull(locationHeader, "Login response header is null.");
        EntityUtils.consume(response.getEntity());

        response = sendGetRequest(client, locationHeader.getValue());
        Map<String, Integer> keyPositionMap = new HashMap<>(1);
        keyPositionMap.put("name=\"sessionDataKeyConsent\"", 1);
        List<DataExtractUtil.KeyValue> keyValues = DataExtractUtil.extractSessionConsentDataFromResponse(response,
                keyPositionMap);
        Assert.assertNotNull(keyValues, "SessionDataKeyConsent key value is null.");

        String sessionDataKeyConsent = keyValues.get(0).getValue();
        EntityUtils.consume(response.getEntity());

        return sessionDataKeyConsent;
    }

    private HttpResponse sendLoginPost(HttpClient client, String sessionDataKey) throws IOException {

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("username", SECONDARY_IS_TEST_USERNAME));
        urlParameters.add(new BasicNameValuePair("password", SECONDARY_IS_TEST_PASSWORD));
        urlParameters.add(new BasicNameValuePair("sessionDataKey", sessionDataKey));

        HttpResponse response = sendPostRequestWithParameters(client, urlParameters, SECONDARY_IS_COMMONAUTH_URL);

        return response;
    }

    private String doConsentApprovalInSecondaryIS(String sessionDataKeyConsent) throws Exception {

        List<NameValuePair> consentParameters = new ArrayList<>();

        HttpResponse response = sendApprovalPostWithConsent(client, sessionDataKeyConsent, consentParameters);
        Assert.assertNotNull(response, "Approval request failed.");

        Header locationHeader = response.getFirstHeader(OAuth2Constant.HTTP_RESPONSE_HEADER_LOCATION);
        EntityUtils.consume(response.getEntity());

        String authzResponseURL = locationHeader.getValue();
        Assert.assertNotNull(authzResponseURL, "Approval request failed for.");

        String authorizeURL = testAuthzCode(authzResponseURL);
        return authorizeURL;
    }

    private HttpResponse sendApprovalPostWithConsent(HttpClient client, String sessionDataKeyConsent,
                                                     List<NameValuePair> consentClaims) throws IOException {

        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("sessionDataKeyConsent", sessionDataKeyConsent));
        urlParameters.add(new BasicNameValuePair("scope-approval", "approve"));
        urlParameters.add(new BasicNameValuePair("user_claims_consent", "true"));
        urlParameters.add(new BasicNameValuePair("consent_select_all", "on"));
        urlParameters.add(new BasicNameValuePair("consent_0", "on"));
        urlParameters.add(new BasicNameValuePair("consent", "approve"));

        if (consentClaims != null) {
            urlParameters.addAll(consentClaims);
        }

        HttpResponse response = sendPostRequestWithParameters(client, urlParameters, SECONDARY_IS_AUTHORIZE_ENDPOINT);
        return response;
    }

    private String testAuthzCode(String authzResponseURL) throws Exception {

        HttpClient httpClientWithoutAutoRedirections = HttpClientBuilder.create().disableRedirectHandling()
                .setDefaultCookieStore(cookieStore).build();

        HttpResponse response = sendGetRequest(httpClientWithoutAutoRedirections, authzResponseURL);
        Assert.assertNotNull(response, "Authorization code response to primary IS is invalid.");

        String locationHeader = getHeaderValue(response, "Location");
        Assert.assertNotNull(locationHeader, "locationHeader not found in response.");

        String pastrCookie = Utils.getPastreCookie(response);
        Assert.assertNotNull(pastrCookie, "pastr cookie not found in response.");

        if (Utils.requestMissingClaims(response)) {
            locationHeader = handleMissingClaims(response, locationHeader, client, pastrCookie);
            Assert.assertNotNull(locationHeader, "locationHeader not found in response.");
        }

        return locationHeader;
    }

    private String handleMissingClaims(HttpResponse response, String locationHeader, HttpClient client, String
            pastrCookie) throws Exception {

        EntityUtils.consume(response.getEntity());

        response = Utils.sendPOSTConsentMessage(response, PRIMARY_IS_IDP_CALLBACK_URL, USER_AGENT, locationHeader,
                client, pastrCookie);
        EntityUtils.consume(response.getEntity());

        return getHeaderValue(response, "Location");
    }

    private String getSAMLResponseFromPrimaryIS(String callbackURL) throws IOException {

        HttpResponse response = sendGetRequest(client, callbackURL);
        return extractValueFromResponse(response, "SAMLResponse", 5);
    }

    private String sendSAMLResponseToWebApp(String samlResponse)
            throws Exception {

        HttpResponse response = getHttpResponseWebApp(samlResponse);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        StringBuffer buffer = new StringBuffer();
        String line = "";
        while ((line = bufferedReader.readLine()) != null) {
            buffer.append(line);
        }
        bufferedReader.close();

        return buffer.toString();
    }

    private HttpResponse getHttpResponseWebApp(String samlResponse) throws IOException {

        HttpPost request = new HttpPost(PRIMARY_IS_SAML_ACS_URL);
        request.setHeader("User-Agent", USER_AGENT);
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("SAMLResponse", samlResponse));
        request.setEntity(new UrlEncodedFormEntity(urlParameters));
        return client.execute(request);
    }

    private boolean validateLoginHomePageContent(String homepageContent) {

        return homepageContent.contains("You are logged in as ");
    }

    private boolean validateLogoutPageContent(String logoutPageContent) {

        return logoutPageContent.contains("location.href = \"index.jsp\"");
    }

    private HttpResponse sendPostRequestWithParameters(HttpClient client, List<NameValuePair> urlParameters, String url)
            throws ClientProtocolException, IOException {

        HttpPost request = new HttpPost(url);
        request.setHeader("User-Agent", OAuth2Constant.USER_AGENT);
        request.setEntity(new UrlEncodedFormEntity(urlParameters));

        HttpResponse response = client.execute(request);
        return response;
    }

    private HttpResponse sendGetRequest(HttpClient client, String locationURL) throws ClientProtocolException,
            IOException {

        HttpGet getRequest = new HttpGet(locationURL);
        getRequest.addHeader("User-Agent", OAuth2Constant.USER_AGENT);
        HttpResponse response = client.execute(getRequest);

        return response;
    }
}
