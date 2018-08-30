/*
 * CredentialsService.java
 *
 * Created on 2018-08-15, 21:46
 */
package com.marcnuri.isotope.api.credentials;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marcnuri.isotope.api.configuration.IsotopeApiConfiguration;
import com.marcnuri.isotope.api.exception.AuthenticationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;

/**
 * Created by Marc Nuri <marc@marcnuri.com> on 2018-08-15.
 */
@Service
public class CredentialsService {

    private final ObjectMapper objectMapper;
    private final IsotopeApiConfiguration isotopeApiConfiguration;

    @Autowired
    public CredentialsService(ObjectMapper objectMapper, IsotopeApiConfiguration isotopeApiConfiguration) {
        this.objectMapper = objectMapper;
        this.isotopeApiConfiguration = isotopeApiConfiguration;
    }

    public void checkHost(Credentials credentials) {
        final Set<String> trustedHosts = isotopeApiConfiguration.getTrustedHosts();
        if (!trustedHosts.isEmpty() && !trustedHosts.contains(credentials.getServerHost())){
            throw new AuthenticationException("Host is not allowed");
        }
    }

    public Credentials encrypt(Credentials credentials) throws JsonProcessingException {
        final Credentials encrytpedCredentials = new Credentials();
        encrytpedCredentials.setSalt(KeyGenerators.string().generateKey());
        final TextEncryptor encryptor = Encryptors.text(isotopeApiConfiguration.getEncryptionPassword(),
                encrytpedCredentials.getSalt());
        encrytpedCredentials.setEncrypted(encryptor.encrypt(objectMapper.writeValueAsString(credentials)));
        return encrytpedCredentials;
    }

    public Credentials decrypt(String encrypted, String salt) throws IOException {
        if(encrypted == null || encrypted.isEmpty() || salt == null || salt.isEmpty()) {
            throw new AuthenticationException("Missing encrypted credentials");
        }
        try {
            final TextEncryptor encryptor = Encryptors.text(isotopeApiConfiguration.getEncryptionPassword(), salt);
            return objectMapper.readValue(encryptor.decrypt(encrypted), Credentials.class);
        } catch(IllegalStateException ex) {
            throw new AuthenticationException("Key or salt is not compatible with encrypted credentials" +
                    " (Server has changed the password or user tampered with credentials.", ex);
        }
    }
}
