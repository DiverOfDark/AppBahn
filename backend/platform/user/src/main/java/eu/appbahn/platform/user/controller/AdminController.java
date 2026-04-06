package eu.appbahn.platform.user.controller;

import eu.appbahn.platform.api.AdminApi;
import eu.appbahn.platform.api.model.PlatformConfig;
import eu.appbahn.platform.api.model.PlatformConfigBranding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController implements AdminApi {

    @Value("${platform.branding.instance-name:AppBahn}")
    private String instanceName;

    @Value("${platform.branding.tagline:Deploy and manage your applications}")
    private String tagline;

    @Value("${platform.branding.logo-url:}")
    private String logoUrl;

    @Value("${platform.branding.login-button-text:Log in with SSO}")
    private String loginButtonText;

    @Override
    public ResponseEntity<PlatformConfig> getPlatformConfig() {
        PlatformConfigBranding branding = new PlatformConfigBranding();
        branding.setInstanceName(instanceName);
        branding.setTagline(tagline);
        branding.setLogoUrl(logoUrl);
        branding.setLoginButtonText(loginButtonText);

        PlatformConfig config = new PlatformConfig();
        config.setBranding(branding);
        return ResponseEntity.ok(config);
    }
}
