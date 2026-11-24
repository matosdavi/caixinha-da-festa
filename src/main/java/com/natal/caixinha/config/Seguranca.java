package com.natal.caixinha.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Um único usuário (o organizador), com a senha vinda da variável ADMIN_SENHA.
 * As páginas das pessoas (/c/{token}) são públicas: quem tem o link, vê.
 */
@Configuration
public class Seguranca {

    private static final Logger log = LoggerFactory.getLogger(Seguranca.class);

    /** "Manter conectado": o Render reinicia o servidor quando ele dorme, e a sessão se perderia. */
    static final Duration LEMBRAR_POR = Duration.ofDays(30);

    private final String usuario;
    private final String senha;

    public Seguranca(@Value("${app.admin.usuario}") String usuario,
                     @Value("${app.admin.senha:}") String senhaConfigurada) {
        this.usuario = usuario;
        if (senhaConfigurada.isBlank()) {
            // Só acontece fora de produção (ConfiguracaoDoAmbiente barra em produção).
            this.senha = gerarSenhaTemporaria();
            log.warn("Nenhuma ADMIN_SENHA configurada. Login temporário do organizador: {} / {}", usuario, senha);
        } else {
            this.senha = senhaConfigurada;
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService organizador(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(User.withUsername(usuario)
                .password(encoder.encode(senha))
                .roles("ORGANIZADOR")
                .build());
    }

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http, UserDetailsService organizador) throws Exception {
        http
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers("/health", "/entrar", "/css/**", "/js/**", "/fontes/**", "/favicon.ico", "/error").permitAll()
                        // link pessoal de cada responsável (fase 2)
                        .requestMatchers("/c/**").permitAll()
                        .anyRequest().hasRole("ORGANIZADOR"))
                .formLogin(login -> login
                        .loginPage("/entrar")
                        .defaultSuccessUrl("/painel", true)
                        .failureUrl("/entrar?erro")
                        .permitAll())
                .rememberMe(lembrar -> lembrar
                        .key(chaveDoLembrar())
                        .rememberMeParameter("lembrar")
                        .tokenValiditySeconds((int) LEMBRAR_POR.toSeconds())
                        .userDetailsService(organizador))
                .logout(sair -> sair
                        .logoutUrl("/sair")
                        .logoutSuccessUrl("/entrar?saiu"))
                .headers(cabecalhos -> cabecalhos
                        // nada deste app deve aparecer em buscadores
                        .addHeaderWriter(new StaticHeadersWriter("X-Robots-Tag", "noindex, nofollow"))
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    /**
     * Chave que assina o cookie "manter conectado". Derivada da senha: continua a mesma
     * quando o servidor reinicia, e trocar a ADMIN_SENHA desconecta todos os aparelhos.
     */
    private String chaveDoLembrar() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("caixinha-lembrar:" + usuario + ":" + senha).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String gerarSenhaTemporaria() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
