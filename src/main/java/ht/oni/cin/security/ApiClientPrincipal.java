package ht.oni.cin.security;

import ht.oni.cin.infrastructure.persistence.entity.CinApiClientEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class ApiClientPrincipal extends AbstractAuthenticationToken {

    private final CinApiClientEntity client;
    private final String apiKey;

    public ApiClientPrincipal(CinApiClientEntity client, String apiKey) {
        super(List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
        this.client = client;
        this.apiKey = apiKey;
        setAuthenticated(true);
    }

    public CinApiClientEntity getClient() {
        return client;
    }

    public String getClientId() {
        return client.getId().toString();
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return this;
    }

    @Override
    public String getName() {
        return client.getClientName();
    }
}
