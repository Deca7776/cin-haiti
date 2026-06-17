package ht.oni.cin.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class OperatorPrincipal extends AbstractAuthenticationToken {

    private final String operatorId;
    private final String token;

    public OperatorPrincipal(String operatorId, String token) {
        super(List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        this.operatorId = operatorId;
        this.token = token;
        setAuthenticated(true);
    }

    public String getOperatorId() {
        return operatorId;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return this;
    }

    @Override
    public String getName() {
        return operatorId;
    }
}
