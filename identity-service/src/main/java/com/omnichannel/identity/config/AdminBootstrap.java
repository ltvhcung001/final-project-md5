package com.omnichannel.identity.config;

import com.omnichannel.identity.entity.Role;
import com.omnichannel.identity.entity.User;
import com.omnichannel.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Creates the first ADMIN account from ADMIN_EMAIL / ADMIN_PASSWORD when it does not exist yet. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${identity.admin.email:}") String email,
                          @Value("${identity.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank() || users.existsByEmail(email)) {
            return;
        }
        users.save(new User(email, encoder.encode(password), "Administrator", null,
                Set.of(Role.ADMIN, Role.CUSTOMER)));
        log.info("Created bootstrap admin account {}", email);
    }
}
